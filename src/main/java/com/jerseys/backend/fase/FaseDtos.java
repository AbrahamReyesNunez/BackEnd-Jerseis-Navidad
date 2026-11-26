package com.jerseys.backend.fase;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class FaseDtos {

    private FaseDtos() {
    }

    /** Las fechas viajan en formato ISO-8601 en UTC, p. ej. "2026-12-01T08:00:00Z". */
    public record FaseResponse(Fase fase, Instant ahora, Instant inicioSubida, Instant finSubida,
                               Instant inicioVotacion, Instant finVotacion) {}

    public record CambiarFechasRequest(
            @NotNull Instant inicioSubida,
            @NotNull Instant finSubida,
            @NotNull Instant inicioVotacion,
            @NotNull Instant finVotacion
    ) {}
}
