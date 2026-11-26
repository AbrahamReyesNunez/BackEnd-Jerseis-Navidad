package com.jerseys.backend.comun;

import java.util.Map;

/**
 * Formato ÚNICO de los errores de la API. El front siempre recibe esto:
 * { "codigo": "YA_HAS_VOTADO", "mensaje": "Ya has votado. Los votos no se pueden cambiar.", "campos": null }
 *
 * "campos" solo aparece en errores de validación: { "email": "no es un email válido" }
 */
public record ErrorRespuesta(String codigo, String mensaje, Map<String, String> campos) {

    public ErrorRespuesta(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }
}
