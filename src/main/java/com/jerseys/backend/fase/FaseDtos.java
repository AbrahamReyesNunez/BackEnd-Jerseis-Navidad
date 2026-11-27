package com.jerseys.backend.fase;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class FaseDtos {

    private FaseDtos() {
    }

    /**
     * Las fechas viajan en formato ISO-8601 en UTC, p. ej. "2026-12-06T11:00:00Z".
     * OJO: en diciembre España va una hora por delante de UTC: las 12:00 de España son las 11:00Z.
     */
    public record FaseResponse(Fase fase, Instant ahora, Instant inicioSubida, Instant finSubida,
                               Instant inicioVotacion, Instant finVotacion, Instant finJurado,
                               Instant publicacionResultados) {}

    /** La decisión del jurado va de finVotacion a finJurado. */
    public record CambiarFechasRequest(
            @NotNull Instant inicioSubida,
            @NotNull Instant finSubida,
            @NotNull Instant inicioVotacion,
            @NotNull Instant finVotacion,
            @NotNull Instant finJurado,
            @NotNull Instant publicacionResultados
    ) {}
}
