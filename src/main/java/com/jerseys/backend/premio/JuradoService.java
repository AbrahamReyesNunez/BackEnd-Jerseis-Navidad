package com.jerseys.backend.premio;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.EstadoJersey;
import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.jersey.JerseyRepository;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.premio.PremioDtos.CategoriaJuradoResponse;
import com.jerseys.backend.premio.PremioDtos.JerseyCandidatoResponse;
import com.jerseys.backend.ranking.RankingService;
import com.jerseys.backend.ranking.RankingService.PosicionRanking;
import com.jerseys.backend.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El jurado elige el ganador de sus cuatro categorías. No votan con puntos: se reúnen en persona,
 * lo hablan, y uno de ellos marca en la web el jersey ganador de cada categoría.
 *
 * Reglas:
 *  - Solo en la fase DECISION_JURADO (justo después de la votación popular).
 *  - El ganador del voto popular no aparece ni se puede elegir: un jersey, un premio.
 *  - Un jersey que ya ha ganado una categoría no puede ganar otra.
 *  - Quien marca no puede elegir su propio jersey (si lo tiene), igual que no puede votárselo.
 *  - Se pueden cambiar de opinión mientras dure la fase: se vuelve a marcar otro jersey
 *    para esa categoría, o se quita el ganador.
 */
@Service
public class JuradoService {

    private final GanadorCategoriaRepository ganadores;
    private final JerseyRepository jerseys;
    private final JerseyService jerseyService;
    private final UsuarioRepository usuarios;
    private final RankingService ranking;
    private final FaseService fases;
    private final Clock reloj;

    public JuradoService(GanadorCategoriaRepository ganadores, JerseyRepository jerseys, JerseyService jerseyService,
                         UsuarioRepository usuarios, RankingService ranking, FaseService fases, Clock reloj) {
        this.ganadores = ganadores;
        this.jerseys = jerseys;
        this.jerseyService = jerseyService;
        this.usuarios = usuarios;
        this.ranking = ranking;
        this.fases = fases;
        this.reloj = reloj;
    }

    /** Las cuatro categorías del jurado, cada una con el ganador elegido (o null). Se puede ver en cualquier fase. */
    @Transactional(readOnly = true)
    public List<CategoriaJuradoResponse> categorias(Long juradoId) {
        Map<Categoria, Jersey> elegidos = ganadoresPorCategoria();
        return Arrays.stream(Categoria.values())
                .filter(Categoria::esDelJurado)
                .map(c -> {
                    Jersey ganador = elegidos.get(c);
                    return new CategoriaJuradoResponse(c, c.nombre(), c.descripcion(),
                            ganador == null ? null : aCandidato(ganador, juradoId, c));
                })
                .toList();
    }

    /** Los jerseys entre los que elige el jurado: todos los del concurso menos el ganador popular. */
    @Transactional(readOnly = true)
    public List<JerseyCandidatoResponse> candidatos(Long juradoId) {
        fases.exigir("elegir ganadores", Fase.DECISION_JURADO);
        Optional<Long> popular = idGanadorPopular();
        Map<Long, Categoria> premiados = ganadoresPorCategoria().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getValue().getId(), Map.Entry::getKey));
        return jerseys.findByEstadoOrderByFechaSubidaAsc(EstadoJersey.APROBADO).stream()
                .filter(j -> popular.map(id -> !id.equals(j.getId())).orElse(true))
                .map(j -> aCandidato(j, juradoId, premiados.get(j.getId())))
                .toList();
    }

    @Transactional
    public CategoriaJuradoResponse elegir(Long juradoId, Categoria categoria, Long jerseyId) {
        fases.exigir("elegir ganadores", Fase.DECISION_JURADO);
        exigirDelJurado(categoria);

        Jersey jersey = jerseys.findById(jerseyId)
                .filter(j -> j.getEstado() == EstadoJersey.APROBADO)
                .orElseThrow(() -> ApiException.peticionInvalida("JERSEY_NO_VALIDO",
                        "El jersey " + jerseyId + " no está en el concurso"));
        if (jersey.esDe(juradoId)) {
            throw ApiException.peticionInvalida("JERSEY_PROPIO",
                    "No puedes elegir tu propio jersey. Que lo marque otro miembro del jurado");
        }
        if (idGanadorPopular().map(jerseyId::equals).orElse(false)) {
            throw ApiException.conflicto("YA_TIENE_PREMIO",
                    "Ese jersey ya ha ganado «" + Categoria.POPULAR.nombre() + "». Solo hay un premio por jersey");
        }
        Optional<GanadorCategoria> otroPremio = ganadores.findByJerseyId(jerseyId)
                .filter(g -> g.getCategoria() != categoria);
        if (otroPremio.isPresent()) {
            throw ApiException.conflicto("YA_TIENE_PREMIO",
                    "Ese jersey ya ha ganado «" + otroPremio.get().getCategoria().nombre()
                            + "». Solo hay un premio por jersey");
        }

        var juez = usuarios.getReferenceById(juradoId);
        var ahora = reloj.instant();
        ganadores.findById(categoria).ifPresentOrElse(
                g -> g.cambiar(jersey, juez, ahora),
                () -> ganadores.save(new GanadorCategoria(categoria, jersey, juez, ahora)));
        // flush manda el cambio a la BD ya: si dos jueces pulsan a la vez, las restricciones de la
        // tabla (una fila por categoría, un premio por jersey) rechazan la segunda con un 409.
        ganadores.flush();

        return new CategoriaJuradoResponse(categoria, categoria.nombre(), categoria.descripcion(),
                aCandidato(jersey, juradoId, categoria));
    }

    /** Deja la categoría sin ganador (por si se equivocaron y quieren ese jersey para otra). */
    @Transactional
    public void quitar(Categoria categoria) {
        fases.exigir("cambiar los ganadores", Fase.DECISION_JURADO);
        exigirDelJurado(categoria);
        ganadores.deleteById(categoria);
    }

    private static void exigirDelJurado(Categoria categoria) {
        if (!categoria.esDelJurado()) {
            throw ApiException.peticionInvalida("CATEGORIA_NO_VALIDA",
                    "El premio popular lo decide la votación, no el jurado");
        }
    }

    private Optional<Long> idGanadorPopular() {
        return ranking.ganadorPopular().map(PosicionRanking::jerseyId);
    }

    private Map<Categoria, Jersey> ganadoresPorCategoria() {
        return ganadores.findAllConJersey().stream()
                .collect(Collectors.toMap(GanadorCategoria::getCategoria, GanadorCategoria::getJersey));
    }

    private JerseyCandidatoResponse aCandidato(Jersey j, Long juradoId, Categoria ganadorDe) {
        return new JerseyCandidatoResponse(j.getId(), jerseyService.url(j), jerseyService.urlTrasera(j),
                j.esDe(juradoId), ganadorDe);
    }
}
