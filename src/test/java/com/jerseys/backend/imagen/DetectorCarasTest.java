package com.jerseys.backend.imagen;

import com.jerseys.backend.comun.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test con OpenCV de verdad (sin Spring ni BD). Imágenes en ImagenesDePrueba. */
class DetectorCarasTest {

    private static DetectorCaras detector;
    private static BufferedImage retrato;

    @BeforeAll
    static void preparar() {
        detector = new DetectorCaras(); // carga OpenCV y el modelo una sola vez para todos los tests
        retrato = ImagenesDePrueba.retrato();
    }

    @Test
    void fotoConCaraSeRechaza() {
        assertThat(detector.contarCaras(retrato)).isEqualTo(1);
        assertThatThrownBy(() -> detector.validar(retrato))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("FOTO_CON_CARA"));
    }

    @Test
    void caraPequenaEnUnaEsquinaTambienSeDetecta() {
        // Una foto grande de un jersey con alguien de fondo, pequeñito, en una esquina
        BufferedImage foto = ImagenesDePrueba.jerseyNavideno(1600, 1200);
        Graphics2D g = foto.createGraphics();
        g.drawImage(retrato, 1350, 50, 200, 258, null);
        g.dispose();

        assertThatThrownBy(() -> detector.validar(foto)).isInstanceOf(ApiException.class);
    }

    @Test
    void jerseyNavidenoSinCarasPasa() {
        // Lleva un muñeco de nieve con ojos y boca: un dibujo no es una cara real
        assertThatCode(() -> detector.validar(ImagenesDePrueba.jerseyNavideno(1200, 900)))
                .doesNotThrowAnyException();
    }

    @Test
    void fotoLisaPasa() {
        assertThatCode(() -> detector.validar(ImagenesDePrueba.lisa(400, 300, Color.BLACK)))
                .doesNotThrowAnyException();
    }
}
