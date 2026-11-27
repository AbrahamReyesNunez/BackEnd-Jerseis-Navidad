package com.jerseys.backend.admin;

import com.jerseys.backend.usuario.Rol;
import com.jerseys.backend.usuario.Usuario;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** borradaEn != null → el usuario borró su cuenta (ya no tiene datos personales). */
    public record UsuarioAdminResponse(Long id, String email, String nombre, Rol rol, boolean verificado,
                                       boolean activo, Instant fechaRegistro, String ipRegistroHash,
                                       Instant borradaEn) {
        public static UsuarioAdminResponse de(Usuario u) {
            return new UsuarioAdminResponse(u.getId(), u.getEmail(), u.getNombre(), u.getRol(), u.isVerificado(),
                    u.isActivo(), u.getFechaRegistro(), u.getIpRegistroHash(), u.getBorradaEn());
        }
    }

    public record CambiarRolRequest(@NotNull Rol rol) {}

    public record CambiarActivoRequest(@NotNull Boolean activo) {}
}
