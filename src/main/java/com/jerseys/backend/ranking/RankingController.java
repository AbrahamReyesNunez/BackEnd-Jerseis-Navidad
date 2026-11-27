package com.jerseys.backend.ranking;

import com.jerseys.backend.ranking.RankingService.PosicionRanking;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Solo ADMIN. La regla está en SeguridadConfig ("/api/ranking/**"):
 * un USUARIO (o un JURADO) recibe 403 aunque llame a esta ruta a mano.
 * Los ganadores (sin puntos) se publican para todos en GET /api/resultados.
 */
@RestController
@Tag(name = "Ranking", description = "Clasificación del voto popular con los puntos (solo admin)")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/api/ranking")
    @Operation(summary = "Ranking del voto popular, con puntos y desglose de votos. 403 si no eres admin")
    public List<PosicionRanking> ranking() {
        return rankingService.calcular();
    }
}
