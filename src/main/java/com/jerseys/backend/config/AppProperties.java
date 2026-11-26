package com.jerseys.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        Imagenes imagenes
) {
    public record Jwt(String secreto, int expiracionHoras) {}

    public record Hash(String secreto) {}

    public record Privacidad(String versionActual) {}

    public record Admin(String email, String password, String nombre) {}

    public record Correo(String remitente) {}

    public record Cors(List<String> origenes) {}

    public record Imagenes(String almacen, String urlBase, String directorioLocal, Ftp ftp) {}

    public record Ftp(String host, int puerto, String usuario, String password, String directorio, boolean ftps) {}
}
