package com.jerseys.backend.usuario;

import java.util.Locale;
import java.util.Set;

/**
 * Convierte un email a su forma "canónica" para detectar cuentas duplicadas.
 *
 * Ejemplos (todos llegan al mismo buzón de Gmail):
 *   Pepe.Garcia+concurso@gmail.com  →  pepegarcia@gmail.com
 *   pepegarcia@googlemail.com       →  pepegarcia@gmail.com
 */
public final class NormalizadorEmail {

    private static final Set<String> DOMINIOS_GMAIL = Set.of("gmail.com", "googlemail.com");

    private NormalizadorEmail() {
    }

    public static String normalizar(String email) {
        String limpio = email.trim().toLowerCase(Locale.ROOT);
        int arroba = limpio.lastIndexOf('@');
        if (arroba < 1) {
            return limpio;
        }
        String local = limpio.substring(0, arroba);
        String dominio = limpio.substring(arroba + 1);

        // Casi todos los proveedores ignoran lo que va tras un "+" (alias)
        int mas = local.indexOf('+');
        if (mas >= 0) {
            local = local.substring(0, mas);
        }
        // Gmail además ignora los puntos
        if (DOMINIOS_GMAIL.contains(dominio)) {
            local = local.replace(".", "");
            dominio = "gmail.com";
        }
        return local + "@" + dominio;
    }
}
