package com.jerseys.backend.premio;

import com.jerseys.backend.premio.PremioDtos.ResultadosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Resultados", description = "Ganadores de los 5 premios, cuando se publican")
public class ResultadosController {

    private final ResultadosService resultados;

    public ResultadosController(ResultadosService resultados) {
        this.resultados = resultados;
    }

    @GetMapping("/api/resultados")
    @Operation(summary = "Ganadores de los 5 premios (sin puntos). Público, pero solo en la fase RESULTADOS: "
            + "antes devuelve 403 FASE_INCORRECTA. ganador = null si un premio quedó desierto")
    public ResultadosResponse resultados() {
        return resultados.publicos();
    }
}
