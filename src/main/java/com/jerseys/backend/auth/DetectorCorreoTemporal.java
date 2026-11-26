package com.jerseys.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Detecta correos TEMPORALES (mailinator, yopmail, 10minutemail...): buzones de usar y tirar
 * que permiten crear cuentas falsas en segundos para votar varias veces.
 *
 * La lista de dominios está en src/main/resources/correos-temporales.txt (un dominio por línea).
 * Se lee UNA sola vez al arrancar y se guarda en un HashSet, así cada comprobación es instantánea.
 *
 * También detecta subdominios: si "mailinator.com" está en la lista, "abc.mailinator.com" también es temporal.
 */
@Component
public class DetectorCorreoTemporal {

    private static final Logger log = LoggerFactory.getLogger(DetectorCorreoTemporal.class);
    private static final String LISTA = "/correos-temporales.txt";

    private final Set<String> dominios;

    public DetectorCorreoTemporal() {
        this.dominios = cargarLista();
        log.info("Lista de correos temporales cargada: {} dominios", dominios.size());
    }

    public boolean esTemporal(String email) {
        String limpio = email.trim().toLowerCase(Locale.ROOT);
        int arroba = limpio.lastIndexOf('@');
        if (arroba < 0) {
            return false; // no es un email: ya lo rechaza la validación del DTO (@Email)
        }
        String dominio = limpio.substring(arroba + 1);

        // Comprueba el dominio y cada dominio "padre": a.b.mailinator.com → b.mailinator.com → mailinator.com
        while (dominio.contains(".")) {
            if (dominios.contains(dominio)) {
                return true;
            }
            dominio = dominio.substring(dominio.indexOf('.') + 1);
        }
        return false;
    }

    private static Set<String> cargarLista() {
        InputStream entrada = DetectorCorreoTemporal.class.getResourceAsStream(LISTA);
        if (entrada == null) {
            // Mejor que la aplicación NO arranque a que arranque sin protección sin que nadie se entere
            throw new IllegalStateException("No se encuentra " + LISTA + " en src/main/resources");
        }
        Set<String> resultado = new HashSet<>();
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                linea = linea.trim().toLowerCase(Locale.ROOT);
                if (!linea.isEmpty() && !linea.startsWith("#")) {
                    resultado.add(linea);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error leyendo " + LISTA, e);
        }
        return resultado;
    }
}
