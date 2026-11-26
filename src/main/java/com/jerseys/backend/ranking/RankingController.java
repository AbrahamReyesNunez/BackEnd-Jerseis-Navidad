package com.jerseys.backend.ranking;

import com.jerseys.backend.ranking.RankingService.PosicionRanking;
import com.jerseys.backend.voto.Categoria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Solo PROFESOR y ADMIN. La regla está en SeguridadConfig ("/api/ranking/**"):
 * un USUARIO (o un JURADO) recibe 403 aunque llame a esta ruta a mano.
 */
@RestController
@Tag(name = "Ranking", description = "Clasificación de cada categoría (solo profesores y admin)")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/api/ranking")
    @Operation(summary = "Ranking por puntos de una categoría: POPULAR (por defecto), DIVERTIDO, ORIGINAL, "
            + "NAVIDENO o EXTRAVAGANTE. 403 si no eres profesor o admin")
    public List<PosicionRanking> ranking(@RequestParam(defaultValue = "POPULAR") Categoria categoria) {
        return rankingService.calcular(categoria);
    }
}
