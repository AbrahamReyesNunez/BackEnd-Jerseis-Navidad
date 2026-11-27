package com.jerseys.backend.voto;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.comun.HashSeguro;
import com.jerseys.backend.correo.CorreoService;
import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.EstadoJersey;
import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.jersey.JerseyRepository;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import com.jerseys.backend.voto.VotoDtos.MiVotoResponse;
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
 * Reglas del voto popular (sistema 3-2-1, decidido por la clase):
 *  - Solo en fase VOTACION, y una única vez por usuario (no se puede rectificar).
 *  - Se reparten de golpe 3 puntos a un jersey, 2 a otro y 1 a otro (jerseys distintos).
 *  - No se puede votar ninguno de tus jerseys.
 *  - Si hay menos de 3 jerseys ajenos, se reparten solo las puntuaciones más altas
 *    (con 2 jerseys: 3 y 2; con 1 jersey: solo el 3).
 *  - Si te ANULAN un voto (el admin, o porque se retira el jersey), puedes volver a dar esos
 *    puntos a otro jersey mientras dure la votación. Los votos que siguen contando no se tocan,
 *    y no puedes volver a votar el jersey cuyo voto te anularon.
 *
 * Las dos situaciones (votar por primera vez y volver a votar) son la misma regla: hay que
 * repartir los "puntos pendientes" (ver papeleta()). La primera vez, los pendientes son 3, 2 y 1.
 *
 * El jurado también vota aquí, como uno más, y su voto cuenta igual. Sus cuatro categorías
 * no se votan con puntos: el jurado elige el ganador de cada una (ver premio/JuradoService).
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
    private final CorreoService correo;
    private final Clock reloj;

    public VotoService(VotoRepository votos, JerseyRepository jerseys, UsuarioRepository usuarios,
                       JerseyService jerseyService, FaseService fases, HashSeguro hash, CorreoService correo,
                       Clock reloj) {
        this.votos = votos;
        this.jerseys = jerseys;
        this.usuarios = usuarios;
        this.jerseyService = jerseyService;
        this.fases = fases;
        this.hash = hash;
        this.correo = correo;
        this.reloj = reloj;
    }

    /**
     * Lo que le queda por votar a un usuario.
     *  - candidatos: jerseys que puede votar (aprobados, no suyos y que no haya votado ya, aunque
     *    ese voto se anulara).
     *  - puntosPendientes: las puntuaciones que no tiene en ningún voto que cuente, de mayor a menor,
     *    y como mucho tantas como candidatos queden. Vacío = no tiene nada que votar.
     *
     * Ejemplos: la primera vez → [3, 2, 1]. Si le anulan el voto de 2 puntos → [2].
     * Con solo 2 jerseys ajenos → [3, 2].
     */
    private record Papeleta(boolean haVotadoAntes, Map<Long, Jersey> candidatos, Set<Long> yaVotados,
                            List<Integer> puntosPendientes) {}

    private Papeleta papeleta(Long usuarioId) {
        List<Voto> suyos = votos.findByUsuarioIdOrderByPuntosDesc(usuarioId);
        Set<Integer> puntosQueCuentan = suyos.stream().filter(v -> !v.isAnulado()).map(Voto::getPuntos)
                .collect(Collectors.toSet());
        Set<Long> yaVotados = suyos.stream().map(v -> v.getJersey().getId()).collect(Collectors.toSet());

        Map<Long, Jersey> candidatos = jerseys.findByEstadoOrderByFechaSubidaAsc(EstadoJersey.APROBADO).stream()
                .filter(j -> !j.esDe(usuarioId) && !yaVotados.contains(j.getId()))
                .collect(Collectors.toMap(Jersey::getId, Function.identity()));

        List<Integer> faltan = IntStream.iterate(MAX_VOTOS, p -> p >= 1, p -> p - 1).boxed()
                .filter(p -> !puntosQueCuentan.contains(p))
                .toList();
        List<Integer> pendientes = faltan.subList(0, Math.min(faltan.size(), candidatos.size()));
        return new Papeleta(!suyos.isEmpty(), candidatos, yaVotados, pendientes);
    }

    /** Puntos que le quedan por repartir (vacío si ya ha votado o no está abierta la votación). Para el front. */
    @Transactional(readOnly = true)
    public List<Integer> puntosPendientes(Long usuarioId) {
        if (fases.faseActual() != Fase.VOTACION) {
            return List.of();
        }
        return papeleta(usuarioId).puntosPendientes();
    }

    /**
     * Comprueba y guarda los votos de un usuario: la papeleta entera la primera vez, o los puntos que
     * le anularon si vuelve a votar. Lo puede hacer cualquiera (también el jurado).
     */
    @Transactional
    public List<MiVotoResponse> votar(Long usuarioId, List<VotoItem> items, DatosCliente cliente) {
        fases.exigir("votar", Fase.VOTACION);
        Papeleta papeleta = papeleta(usuarioId);
        List<Integer> pendientes = papeleta.puntosPendientes();
        if (pendientes.isEmpty()) {
            throw papeleta.haVotadoAntes()
                    ? ApiException.conflicto("YA_HAS_VOTADO", "Ya has votado. Los votos no se pueden cambiar.")
                    : ApiException.conflicto("NO_HAY_JERSEYS", "No hay jerseys que puedas votar");
        }
        String puntosTexto = pendientes.stream().map(String::valueOf).collect(Collectors.joining(", "));
        if (items.size() != pendientes.size()) {
            throw ApiException.peticionInvalida("NUMERO_DE_VOTOS_INCORRECTO", "Tienes que repartir exactamente "
                    + pendientes.size() + (pendientes.size() == 1 ? " voto" : " votos") + " (" + puntosTexto + ")");
        }

        Set<Long> jerseysDeEstaPapeleta = new HashSet<>();
        for (VotoItem item : items) {
            if (!jerseysDeEstaPapeleta.add(item.jerseyId())) {
                throw ApiException.peticionInvalida("JERSEY_REPETIDO", "No puedes votar dos veces el mismo jersey");
            }
            if (!papeleta.candidatos().containsKey(item.jerseyId())) {
                throw porQueNoSePuedeVotar(usuarioId, item.jerseyId(), papeleta);
            }
        }

        // Las puntuaciones deben ser exactamente las pendientes: {3, 2, 1}, {3, 2}, {3}, o las que te anularon
        Set<Integer> recibidas = items.stream().map(VotoItem::puntos).collect(Collectors.toSet());
        if (!recibidas.equals(Set.copyOf(pendientes)) || recibidas.size() != items.size()) {
            throw ApiException.peticionInvalida("PUNTOS_INCORRECTOS",
                    "Debes usar una vez cada puntuación: " + puntosTexto);
        }

        Usuario votante = usuarios.getReferenceById(usuarioId);
        Instant ahora = reloj.instant();
        String ipHash = hash.hmac(cliente.ip());
        List<Voto> nuevos = items.stream()
                .map(i -> new Voto(votante, papeleta.candidatos().get(i.jerseyId()), i.puntos(), ahora, ipHash,
                        cliente.userAgent()))
                .toList();
        // saveAllAndFlush manda los INSERT ya: si dos peticiones llegan a la vez, los índices únicos
        // de la BD rechazan la segunda (y ManejadorErrores responde 409). Ver V9.
        votos.saveAllAndFlush(nuevos);

        return nuevos.stream().map(this::aMiVoto).toList();
    }

    private ApiException porQueNoSePuedeVotar(Long usuarioId, Long jerseyId, Papeleta papeleta) {
        if (papeleta.yaVotados().contains(jerseyId)) {
            return ApiException.peticionInvalida("JERSEY_YA_VOTADO",
                    "Ya votaste ese jersey. Elige otro para los puntos que te faltan");
        }
        boolean esPropio = jerseys.findById(jerseyId).map(j -> j.esDe(usuarioId)).orElse(false);
        return esPropio
                ? ApiException.peticionInvalida("VOTO_PROPIO", "No puedes votar tu propio jersey")
                : ApiException.peticionInvalida("JERSEY_NO_VALIDO", "El jersey " + jerseyId + " no está en el concurso");
    }

    /** Tus votos, también los anulados (anulado = true: ya no cuenta). */
    @Transactional(readOnly = true)
    public List<MiVotoResponse> misVotos(Long usuarioId) {
        return votos.findByUsuarioIdOrderByPuntosDesc(usuarioId).stream().map(this::aMiVoto).toList();
    }

    @Transactional(readOnly = true)
    public List<VotoAdminResponse> listarParaAdmin() {
        return votos.findAllParaAdmin().stream()
                .map(v -> new VotoAdminResponse(v.getId(), v.getUsuario().getId(), v.getUsuario().getEmail(),
                        v.getJersey().getId(), v.getPuntos(), v.getFecha(), v.getIpHash(),
                        v.getUserAgent(), v.isAnulado()))
                .toList();
    }

    /**
     * Solo durante la VOTACION. Después ya no: al acabar la votación el jurado empieza a elegir
     * ganadores sin contar el ganador popular, y si el ganador popular cambiara a esas alturas
     * podría coincidir con el de una categoría del jurado (un jersey con dos premios).
     */
    @Transactional
    public void anular(Long adminId, Long votoId) {
        fases.exigir("anular votos", Fase.VOTACION);
        Voto voto = votos.findById(votoId)
                .orElseThrow(() -> ApiException.noEncontrado("VOTO_NO_ENCONTRADO", "No existe ese voto"));
        if (voto.isAnulado()) {
            return; // ya estaba anulado: no avisamos otra vez
        }
        voto.anular(usuarios.getReferenceById(adminId));
        avisarVotoAnulado(voto, "La organización lo ha anulado al revisar los votos.");
    }

    /**
     * Los jerseys se han retirado del concurso (su autor ha borrado la cuenta): los votos que tenían
     * dejan de contar, y avisamos por correo a cada persona que los había votado.
     */
    @Transactional
    public void anularVotosA(List<Jersey> retirados) {
        for (Jersey jersey : retirados) {
            for (Voto voto : votos.findValidosAlJersey(jersey.getId())) {
                voto.anular(null); // nadie lo ha anulado a mano: ha desaparecido el jersey
                avisarVotoAnulado(voto, "El jersey al que se lo diste se ha retirado del concurso.");
            }
        }
    }

    /** Al borrar la cuenta durante la votación: sus votos desaparecen, como si no hubiera votado. */
    @Transactional
    public void borrarVotosDe(Long usuarioId) {
        votos.borrarLosDe(usuarioId);
    }

    /** Al borrar la cuenta después de la votación: los votos se quedan (ya cuentan para los premios), sin IP. */
    @Transactional
    public void anonimizarVotosDe(Long usuarioId) {
        votos.anonimizarLosDe(usuarioId);
    }

    private void avisarVotoAnulado(Voto voto, String motivo) {
        Usuario votante = voto.getUsuario();
        if (!votante.estaBorrada()) {
            correo.enviarVotoAnulado(votante.getEmail(), votante.getNombre(), voto.getPuntos(), motivo);
        }
    }

    private MiVotoResponse aMiVoto(Voto v) {
        return new MiVotoResponse(v.getJersey().getId(), jerseyService.url(v.getJersey()), v.getPuntos(), v.getFecha(),
                v.isAnulado());
    }
}
