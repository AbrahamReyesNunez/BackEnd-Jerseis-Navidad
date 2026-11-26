package com.jerseys.backend.jersey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class JerseyDtos {

    private JerseyDtos() {
    }

    /** Lo que ve cada usuario de SU propio jersey. */
    public record MiJerseyResponse(Long id, String imagenUrl, EstadoJersey estado, String motivoEliminacion,
                                   Instant fechaSubida) {}

    /**
     * Jersey en la lista de votación. Es anónimo a propósito (no dice de quién es),
     * para que se vote la foto y no a la persona.
     */
    public record JerseyVotableResponse(Long id, String imagenUrl, boolean esPropio) {}

    /** Vista completa para el panel de administración. */
    public record JerseyAdminResponse(Long id, String imagenUrl, EstadoJersey estado, Long usuarioId,
                                      String usuarioNombre, String usuarioEmail, Instant fechaSubida,
                                      String motivoEliminacion, Instant fechaEliminacion) {}

    public record EliminarJerseyRequest(
            @NotBlank(message = "Indica el motivo") @Size(max = 500) String motivo
    ) {}
}
