package com.jerseys.backend.usuario;

/**
 * Roles de la aplicación.
 * <ul>
 *   <li>USUARIO: sube sus jerseys y vota en el voto popular.</li>
 *   <li>JURADO: vota en el voto popular como todos y, además, en la fase DECISION_JURADO elige
 *       el ganador de las 4 categorías del jurado (divertido, original, navideño, extravagante).
 *       No ve el ranking ni los votos.</li>
 *   <li>ADMIN: gestiona usuarios (y nombra al jurado), jerseys, votos y fechas. El ÚNICO que ve
 *       los votos y el ranking.</li>
 * </ul>
 */
public enum Rol {
    USUARIO,
    JURADO,
    ADMIN
}
