package com.jerseys.backend.cuenta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CuentaDtos {

    private CuentaDtos() {
    }

    /** { "passwordActual": "...", "passwordNueva": "..." } */
    public record CambiarPasswordRequest(
            @NotBlank(message = "Escribe tu contraseña actual") String passwordActual,

            // Mismas reglas que en el registro
            @NotBlank(message = "La contraseña nueva es obligatoria")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            String passwordNueva
    ) {}

    /** Para borrar la cuenta hay que escribir la contraseña: { "password": "..." } */
    public record BorrarCuentaRequest(@NotBlank(message = "Escribe tu contraseña para confirmar") String password) {}
}
