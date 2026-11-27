package com.jerseys.backend.premio;

import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.premio.PremioDtos.GanadorResponse;
import com.jerseys.backend.premio.PremioDtos.PremioResponse;
import com.jerseys.backend.premio.PremioDtos.ResultadosResponse;
import com.jerseys.backend.ranking.RankingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Los ganadores de los 5 premios. Solo dice QUIÉN ha ganado, nunca los puntos ni los votos
 * (eso solo lo ve el admin en /api/ranking).
 */
@Service
public class ResultadosService {

    private final RankingService ranking;
    private final GanadorCategoriaRepository ganadores;
    private final JerseyService jerseyService;
    private final FaseService fases;

    public ResultadosService(RankingService ranking, GanadorCategoriaRepository ganadores,
                             JerseyService jerseyService, FaseService fases) {
        this.ranking = ranking;
        this.ganadores = ganadores;
        this.jerseyService = jerseyService;
        this.fases = fases;
    }

    /** Para todo el mundo, pero solo cuando ya se han publicado (fase RESULTADOS). */
    @Transactional(readOnly = true)
    public ResultadosResponse publicos() {
        fases.exigir("ver los resultados", Fase.RESULTADOS);
        return calcular();
    }

    /** Sin comprobar la fase: para que el admin los vea antes de publicarlos. */
    @Transactional(readOnly = true)
    public ResultadosResponse calcular() {
        List<PremioResponse> premios = new ArrayList<>();

        GanadorResponse popular = ranking.ganadorPopular()
                .map(p -> new GanadorResponse(p.jerseyId(), p.imagenUrl(), p.imagenTraseraUrl(), p.autor()))
                .orElse(null);
        premios.add(premio(Categoria.POPULAR, popular));

        Map<Categoria, Jersey> delJurado = ganadores.findAllConJersey().stream()
                .collect(Collectors.toMap(GanadorCategoria::getCategoria, GanadorCategoria::getJersey));
        for (Categoria c : Categoria.values()) {
            if (c.esDelJurado()) {
                Jersey j = delJurado.get(c);
                premios.add(premio(c, j == null ? null : new GanadorResponse(j.getId(), jerseyService.url(j),
                        jerseyService.urlTrasera(j), j.getUsuario().getNombre())));
            }
        }
        return new ResultadosResponse(premios);
    }

    private static PremioResponse premio(Categoria c, GanadorResponse ganador) {
        return new PremioResponse(c, c.nombre(), c.descripcion(), ganador);
    }
}
