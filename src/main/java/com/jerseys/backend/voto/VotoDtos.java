package com.jerseys.backend.voto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class VotoDtos {

    private VotoDtos() {
    }

    /**
     * Ejemplo de JSON que manda el front (sistema tipo Eurovisión):
     * { "votos": [ {"jerseyId": 12, "puntos": 6}, {"jerseyId": 3, "puntos": 5}, ..., {"jerseyId": 8, "puntos": 1} ] }
     */
    public record VotarRequest(
            @NotEmpty(message = "Debes enviar tus votos") @Valid List<VotoItem> votos
    ) {}

    public record VotoItem(
            @NotNull Long jerseyId,
            @NotNull @Min(1) @Max(6) Integer puntos
    ) {}

    public record MiVotoResponse(Long jerseyId, String imagenUrl, int puntos, Instant fecha) {}

    public record VotoAdminResponse(Long id, Long votanteId, String votanteEmail, Long jerseyId, int puntos,
                                    Instant fecha, String ipHash, String userAgent, boolean anulado) {}
}
