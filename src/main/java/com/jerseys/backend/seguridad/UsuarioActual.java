package com.jerseys.backend.seguridad;

import com.jerseys.backend.usuario.Rol;

/**
 * Quién está haciendo la petición actual. Lo rellena FiltroJwt a partir del token.
 *
 * En un controlador se obtiene así:
 *     public X miMetodo(@AuthenticationPrincipal UsuarioActual yo) { ... yo.id() ... }
 */
public record UsuarioActual(Long id, String email, Rol rol) {
}
