package com.jerseys.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Todas las propiedades "app.*" de application.properties, en un objeto Java con tipos.
 * Spring rellena este record al arrancar. Para usarlo, pídelo en un constructor:
 *     public MiServicio(AppProperties props) { ... }
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Hash hash,
        String urlPublica,
        Privacidad privacidad,
        Admin admin,
        Correo correo,
        Cors cors,
        Imagenes imagenes,
        Limites limites,
        Captcha captcha
) {
    public record Jwt(String secreto, int expiracionHoras) {}

    public record Hash(String secreto) {}

    public record Privacidad(String versionActual) {}

    public record Admin(String email, String password, String nombre) {}

    /** Ver CorreoService. remitente vacío = se usa el usuario SMTP. */
    public record Correo(String remitente, String nombreRemitente, boolean obligatorio) {}

    public record Cors(List<String> origenes) {}

    public record Imagenes(String almacen, String urlBase, String directorioLocal, Ftp ftp) {}

    public record Ftp(String host, int puerto, String usuario, String password, String directorio, boolean ftps) {}

    /** Límites de peticiones por IP. Ver LimitadorPeticiones. */
    public record Limites(Limite login, Limite registro, Limite reenviarVerificacion) {}

    /** Como máximo "capacidad" peticiones cada "periodo" (p. ej. 30 cada 1m). */
    public record Limite(int capacidad, Duration periodo) {}

    /** CAPTCHA del registro (Cloudflare Turnstile). Ver VerificadorCaptcha. */
    public record Captcha(boolean activado, String secreto) {}
}
