package com.jerseys.backend.imagen;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Solo en desarrollo (almacén local): Spring sirve las fotos en /imagenes/<nombre>.jpg
 * En producción NO se usa: las sirve Nginx directamente, que es mucho más eficiente.
 */
@Configuration
@ConditionalOnProperty(name = "app.imagenes.almacen", havingValue = "local")
public class ImagenesLocalesConfig implements WebMvcConfigurer {

    private final AlmacenLocal almacen;

    public ImagenesLocalesConfig(AlmacenLocal almacen) {
        this.almacen = almacen;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/imagenes/**")
                .addResourceLocations(almacen.getCarpeta().toUri().toString());
    }
}
