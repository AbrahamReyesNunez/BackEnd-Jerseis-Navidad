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
     * Ejemplo de JSON que manda el front (sistema 3-2-1):
     * { "votos": [ {"jerseyId": 12, "puntos": 3}, {"jerseyId": 3, "puntos": 2}, {"jerseyId": 8, "puntos": 1} ] }
     */
    public record VotarRequest(
            @NotEmpty(message = "Debes enviar tus votos") @Valid List<VotoItem> votos
    ) {}

    public record VotoItem(
            @NotNull Long jerseyId,
            @NotNull @Min(1) @Max(VotoService.MAX_VOTOS) Integer puntos
    ) {}

    /**
     * Voto del jurado en UNA categoría (se envía una petición por categoría):
     * { "categoria": "DIVERTIDO", "votos": [ {"jerseyId": 12, "puntos": 3}, ... ] }
     */
    public record VotarJuradoRequest(
            @NotNull(message = "Indica la categoría") Categoria categoria,
            @NotEmpty(message = "Debes enviar tus votos") List<@Valid VotoItem> votos
    ) {}

    public record MiVotoResponse(Long jerseyId, String imagenUrl, int puntos, Instant fecha) {}

    /** Una categoría del jurado y lo que ha votado en ella este jurado ("votado" = false si aún no). */
    public record CategoriaJuradoResponse(Categoria categoria, String nombre, String descripcion, boolean votado,
                                          List<MiVotoResponse> votos) {}

    public record VotoAdminResponse(Long id, Long votanteId, String votanteEmail, Categoria categoria,
                                    Long jerseyId, int puntos, Instant fecha, String ipHash, String userAgent,
                                    boolean anulado) {}
}
