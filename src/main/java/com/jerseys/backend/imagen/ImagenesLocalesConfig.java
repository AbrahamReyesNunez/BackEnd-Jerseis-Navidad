package com.jerseys.backend.imagen;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Con el almacén local (en vuestro ordenador), el propio backend sirve las fotos en /imagenes/<nombre>.jpg
 * En producción las fotos van al FTP y las enseña otro servidor (ver GUIA.md, sección 11).
 */
@Configuration
@ConditionalOnProperty(name = "app.imagenes.almacen", havingValue = "local")
public class ImagenesLocalesConfig implements WebMvcConfigurer {

    /**
     * Cuánto pueden guardar las fotos Cloudflare y los navegadores sin volver a pedirlas.
     * Corto a propósito: si el admin retira una foto inapropiada, deja de verse en 5 minutos
     * como mucho. (Sin esto, Cloudflare podría seguir enseñándola un par de horas.)
     */
    static final Duration CACHE_FOTOS = Duration.ofMinutes(5);

    private final AlmacenLocal almacen;

    public ImagenesLocalesConfig(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/imagenes/**")
                .addResourceLocations(almacen.getCarpeta().toUri().toString())
                .setCacheControl(CacheControl.maxAge(CACHE_FOTOS).cachePublic());
    }
}
