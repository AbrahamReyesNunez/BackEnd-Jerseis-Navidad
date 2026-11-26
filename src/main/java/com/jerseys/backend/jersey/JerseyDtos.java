package com.jerseys.backend.jersey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class JerseyDtos {

    private JerseyDtos() {
    }

    /** Lo que ve cada usuario de cada uno de SUS jerseys. */
    public record MiJerseyResponse(Long id, String imagenUrl, EstadoJersey estado, String motivoEliminacion,
                                   Instant fechaSubida) {}

    /**
     * "Mis jerseys": todos los que ha subido el usuario (también los eliminados, para que vea el motivo).
     * El front puede usar maximo y activos para mostrar "Te quedan X por subir".
     */
    public record MisJerseysResponse(int maximo, int activos, List<MiJerseyResponse> jerseys) {}

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
