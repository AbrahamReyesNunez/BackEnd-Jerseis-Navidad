package com.jerseys.backend.comun;

import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio. Al lanzarla desde cualquier servicio, ManejadorErrores la
 * convierte en una respuesta JSON { "codigo": ..., "mensaje": ... } con su código HTTP.
 *
 * Ejemplo:  throw ApiException.conflicto("YA_HAS_VOTADO", "Ya has votado");
 */
public class ApiException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;

    public ApiException(HttpStatus estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public static ApiException peticionInvalida(String codigo, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }

    public static ApiException noAutorizado(String codigo, String mensaje) {
        return new ApiException(HttpStatus.UNAUTHORIZED, codigo, mensaje);
    }

    public static ApiException prohibido(String codigo, String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, codigo, mensaje);
    }

    public static ApiException noEncontrado(String codigo, String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, codigo, mensaje);
    }

    public static ApiException conflicto(String codigo, String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensaje);
    }

    public static ApiException demasiadasPeticiones(String codigo, String mensaje) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, codigo, mensaje);
    }

    public static ApiException servicioNoDisponible(String codigo, String mensaje) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensaje);
    }

    public HttpStatus getEstado() { return estado; }
    public String getCodigo() { return codigo; }
}
