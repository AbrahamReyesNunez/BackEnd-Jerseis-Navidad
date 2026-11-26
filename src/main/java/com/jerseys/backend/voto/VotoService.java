package com.jerseys.backend.voto;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.comun.HashSeguro;
import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.EstadoJersey;
import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.jersey.JerseyRepository;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import com.jerseys.backend.voto.VotoDtos.MiVotoResponse;
import com.jerseys.backend.voto.VotoDtos.VotarRequest;
import com.jerseys.backend.voto.VotoDtos.VotoAdminResponse;
import com.jerseys.backend.voto.VotoDtos.VotoItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reglas de votación (sistema 3-2-1, decidido por la clase):
 *  - Solo en fase VOTACION, y una única vez por usuario (no se puede rectificar).
 *  - Se reparten de golpe 3 puntos a un jersey, 2 a otro y 1 a otro (jerseys distintos).
 *  - No se puede votar el propio jersey.
 *  - Si hay menos de 3 jerseys ajenos, se reparten solo las puntuaciones más altas
 *    (con 2 jerseys: 3 y 2; con 1 jersey: solo el 3).
 *
 * Para cambiar el número de puntos basta con MAX_VOTOS, pero hay que acompañarlo de una
 * migración de Flyway que cambie la restricción CHECK de la tabla votos (ver V2).
 */
@Service
public class VotoService {

    /** Cuántos votos reparte cada usuario y, a la vez, la puntuación más alta (3 → 3, 2, 1). */
    public static final int MAX_VOTOS = 3;

    private final VotoRepository votos;
    private final JerseyRepository jerseys;
    private final UsuarioRepository usuarios;
    private final JerseyService jerseyService;
    private final FaseService fases;
    private final HashSeguro hash;
    private final Clock reloj;

    public VotoService(VotoRepository votos, JerseyRepository jerseys, UsuarioRepository usuarios,
                       JerseyService jerseyService, FaseService fases, HashSeguro hash, Clock reloj) {
        this.votos = votos;
        this.jerseys = jerseys;
        this.usuarios = usuarios;
        this.jerseyService = jerseyService;
        this.fases = fases;
        this.hash = hash;
        this.reloj = reloj;
    }

    @Transactional
    public List<MiVotoResponse> votar(Long usuarioId, VotarRequest peticion, DatosCliente cliente) {
        fases.exigir("votar", Fase.VOTACION);
        if (votos.existsByUsuarioId(usuarioId)) {
            throw ApiException.conflicto("YA_HAS_VOTADO", "Ya has votado. Los votos no se pueden cambiar.");
        }

        // Jerseys que este usuario puede votar: aprobados y que no sean suyos
        Map<Long, Jersey> votables = jerseys.findByEstadoOrderByFechaSubidaAsc(EstadoJersey.APROBADO).stream()
                .filter(j -> !j.esDe(usuarioId))
                .collect(Collectors.toMap(Jersey::getId, Function.identity()));

        int votosNecesarios = Math.min(MAX_VOTOS, votables.size());
        if (votosNecesarios == 0) {
            throw ApiException.conflicto("NO_HAY_JERSEYS", "No hay jerseys que puedas votar");
        }
        List<VotoItem> items = peticion.votos();
        if (items.size() != votosNecesarios) {
            throw ApiException.peticionInvalida("NUMERO_DE_VOTOS_INCORRECTO",
                    "Tienes que repartir exactamente " + votosNecesarios + " votos");
        }

        Set<Long> jerseysVotados = new HashSet<>();
        for (VotoItem item : items) {
            if (!jerseysVotados.add(item.jerseyId())) {
                throw ApiException.peticionInvalida("JERSEY_REPETIDO", "No puedes votar dos veces el mismo jersey");
            }
            if (!votables.containsKey(item.jerseyId())) {
                boolean esPropio = jerseys.findById(item.jerseyId()).map(j -> j.esDe(usuarioId)).orElse(false);
                throw esPropio
                        ? ApiException.peticionInvalida("VOTO_PROPIO", "No puedes votar tu propio jersey")
                        : ApiException.peticionInvalida("JERSEY_NO_VALIDO",
                        "El jersey " + item.jerseyId() + " no está en el concurso");
            }
        }

        // Las puntuaciones deben ser exactamente las N más altas: {3, 2, 1}, {3, 2} o {3}
        Set<Integer> esperadas = IntStream.rangeClosed(MAX_VOTOS - votosNecesarios + 1, MAX_VOTOS)
                .boxed().collect(Collectors.toSet());
        Set<Integer> recibidas = items.stream().map(VotoItem::puntos).collect(Collectors.toSet());
        if (!recibidas.equals(esperadas) || recibidas.size() != items.size()) {
            throw ApiException.peticionInvalida("PUNTOS_INCORRECTOS",
                    "Debes usar una vez cada puntuación: " + esperadas.stream().sorted().map(String::valueOf)
                            .collect(Collectors.joining(", ")));
        }

        Usuario votante = usuarios.getReferenceById(usuarioId);
        Instant ahora = reloj.instant();
        String ipHash = hash.hmac(cliente.ip());
        List<Voto> nuevos = items.stream()
                .map(i -> new Voto(votante, votables.get(i.jerseyId()), i.puntos(), ahora, ipHash, cliente.userAgent()))
                .toList();
        // saveAllAndFlush manda los INSERT ya: si dos peticiones llegan a la vez, la restricción
        // UNIQUE de la BD rechaza la segunda (y ManejadorErrores responde 409).
        votos.saveAllAndFlush(nuevos);

        return nuevos.stream().map(this::aMiVoto).toList();
    }

    @Transactional(readOnly = true)
    public List<MiVotoResponse> misVotos(Long usuarioId) {
        return votos.findByUsuarioIdOrderByPuntosDesc(usuarioId).stream().map(this::aMiVoto).toList();
    }

    @Transactional(readOnly = true)
    public List<VotoAdminResponse> listarParaAdmin() {
        return votos.findAllParaAdmin().stream()
                .map(v -> new VotoAdminResponse(v.getId(), v.getUsuario().getId(), v.getUsuario().getEmail(),
                        v.getJersey().getId(), v.getPuntos(), v.getFecha(), v.getIpHash(), v.getUserAgent(),
                        v.isAnulado()))
                .toList();
    }

    @Transactional
    public void anular(Long adminId, Long votoId) {
        Voto voto = votos.findById(votoId)
                .orElseThrow(() -> ApiException.noEncontrado("VOTO_NO_ENCONTRADO", "No existe ese voto"));
        voto.anular(usuarios.getReferenceById(adminId));
    }

    private MiVotoResponse aMiVoto(Voto v) {
        return new MiVotoResponse(v.getJersey().getId(), jerseyService.url(v.getJersey()), v.getPuntos(), v.getFecha());
    }
}
