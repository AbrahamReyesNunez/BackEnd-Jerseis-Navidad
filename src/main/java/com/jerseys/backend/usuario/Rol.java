package com.jerseys.backend.usuario;

/**
 * Roles de la aplicación.
 * <ul>
 *   <li>USUARIO: sube su jersey y vota.</li>
 *   <li>PROFESOR: además, ve el ranking.</li>
 *   <li>ADMIN: además, gestiona usuarios, jerseys, votos y fechas.</li>
 * </ul>
 */
public enum Rol {
    USUARIO,
    PROFESOR,
    ADMIN
}
