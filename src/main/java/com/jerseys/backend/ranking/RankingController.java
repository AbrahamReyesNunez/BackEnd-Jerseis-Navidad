package com.jerseys.backend.ranking;

import com.jerseys.backend.ranking.RankingService.PosicionRanking;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Solo PROFESOR y ADMIN. La regla está en SeguridadConfig ("/api/ranking/**"):
 * un USUARIO recibe 403 aunque llame a esta ruta a mano.
 */
@RestController
@Tag(name = "Ranking", description = "Clasificación (solo profesores y admin)")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/api/ranking")
    @Operation(summary = "Ranking actual por puntos. 403 si no eres profesor o admin")
    public List<PosicionRanking> ranking() {
        return rankingService.calcular();
    }
}
