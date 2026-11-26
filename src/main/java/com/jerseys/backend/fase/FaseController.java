package com.jerseys.backend.fase;

import com.jerseys.backend.fase.FaseDtos.FaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Fase", description = "En qué fase está el concurso")
public class FaseController {

    private final FaseService faseService;

    public FaseController(FaseService faseService) {
        this.faseService = faseService;
    }

    @GetMapping("/api/fase")
    @Operation(summary = "Fase actual y fechas. Público: el front lo usa para decidir qué pantalla enseñar")
    public FaseResponse fase() {
        return faseService.estado();
    }
}
