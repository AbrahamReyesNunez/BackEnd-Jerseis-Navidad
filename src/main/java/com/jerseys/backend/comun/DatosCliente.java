package com.jerseys.backend.comun;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Datos del cliente que hace la petición, para el sistema antifraude.
 * Gracias a server.forward-headers-strategy=native, getRemoteAddr() ya devuelve
 * la IP real del visitante aunque la petición pase por Nginx.
 */
public record DatosCliente(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 300;

    public static DatosCliente de(HttpServletRequest peticion) {
        String agente = peticion.getHeader(HttpHeaders.USER_AGENT);
        if (agente != null && agente.length() > MAX_USER_AGENT) {
            agente = agente.substring(0, MAX_USER_AGENT);
        }
        return new DatosCliente(peticion.getRemoteAddr(), agente);
    }
}
