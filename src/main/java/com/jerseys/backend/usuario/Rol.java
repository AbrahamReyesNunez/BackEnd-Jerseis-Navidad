package com.jerseys.backend.usuario;

/**
 * Roles de la aplicación.
 * <ul>
 *   <li>USUARIO: sube sus jerseys y vota en el voto popular.</li>
 *   <li>PROFESOR: además, ve el ranking.</li>
 *   <li>JURADO: vota en el voto popular como todos y, además, en las 4 categorías del jurado
 *       (divertido, original, navideño, extravagante). No ve el ranking, para no influirle.</li>
 *   <li>ADMIN: gestiona usuarios (y nombra al jurado), jerseys, votos y fechas.</li>
 * </ul>
 */
public enum Rol {
    USUARIO,
    PROFESOR,
    JURADO,
    ADMIN
}
