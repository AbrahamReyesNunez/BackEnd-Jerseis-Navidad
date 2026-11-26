package com.jerseys.backend.auth.dto;

import com.jerseys.backend.usuario.Rol;
import com.jerseys.backend.usuario.Usuario;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs (Data Transfer Objects): la "forma" del JSON que entra y sale de la API.
 *
 * ¿Por qué no devolver directamente la entidad Usuario? Porque tiene campos que
 * NUNCA deben salir (passwordHash, ipRegistroHash...). El DTO decide qué se expone.
 *
 * Las anotaciones (@NotBlank, @Email...) se comprueban solas gracias a @Valid en el controlador.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegistroRequest(
            @NotBlank(message = "El email es obligatorio")
            @Email(message = "No es un email válido")
            @Size(max = 254)
            String email,

            @NotBlank(message = "El nombre es obligatorio")
            @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
            String nombre,

            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            String password,

            // El backend comprueba las casillas: no basta con que el front las exija
            @AssertTrue(message = "Debes aceptar la política de privacidad")
            boolean aceptaPrivacidad,

            @AssertTrue(message = "Debes dar tu consentimiento")
            boolean aceptaConsentimiento,

            @AssertTrue(message = "Debes tener 14 años o más")
            boolean mayorDe14
    ) {}

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {}

    public record LoginResponse(String token, long expiraEnSegundos, UsuarioResponse usuario) {}

    public record ReenviarRequest(@NotBlank @Email String email) {}

    public record UsuarioResponse(Long id, String email, String nombre, Rol rol) {
        public static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getEmail(), u.getNombre(), u.getRol());
        }
    }

    public record MensajeResponse(String mensaje) {}
}
