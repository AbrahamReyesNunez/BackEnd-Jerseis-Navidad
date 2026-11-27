package com.jerseys.backend.config;

import com.jerseys.backend.config.AppProperties.Imagenes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @Test
    void laDireccionDeLasFotosSiempreAcabaEnBarra() {
        // Si alguien pone IMAGENES_URL_BASE sin la "/" final, las URLs de las fotos no se rompen
        assertThat(new Imagenes("ftp", "https://fotos.ejemplo.es/imagenes", null, null).urlBase())
                .isEqualTo("https://fotos.ejemplo.es/imagenes/");
        assertThat(new Imagenes("ftp", "/imagenes/", null, null).urlBase()).isEqualTo("/imagenes/");
    }
}
