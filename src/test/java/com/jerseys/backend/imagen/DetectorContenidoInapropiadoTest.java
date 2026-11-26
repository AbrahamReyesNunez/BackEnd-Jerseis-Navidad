package com.jerseys.backend.imagen;

import com.jerseys.backend.comun.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test con el modelo open_nsfw de verdad (sin Spring ni BD).
 *
 * Por razones obvias, el repositorio NO tiene fotos inapropiadas: comprobamos que las fotos
 * normales pasan con una probabilidad baja, y la regla del umbral por separado.
 */
class DetectorContenidoInapropiadoTest {

    private static DetectorContenidoInapropiado detector;

    @BeforeAll
    static void preparar() {
        detector = new DetectorContenidoInapropiado(); // carga el modelo una sola vez
    }

    @Test
    void unJerseyNavidenoTieneProbabilidadBaja() {
        double p = detector.probabilidadInapropiada(ImagenesDePrueba.jerseyNavideno(1200, 900));
        assertThat(p).isBetween(0.0, 0.2);
        assertThatCode(() -> detector.validar(ImagenesDePrueba.jerseyNavideno(1200, 900)))
                .doesNotThrowAnyException();
    }

    @Test
    void unRetratoDeUnaPersonaVestidaTieneProbabilidadBaja() {
        assertThat(detector.probabilidadInapropiada(ImagenesDePrueba.retrato())).isBetween(0.0, 0.2);
    }

    @Test
    void unaFotoLisaColorPielNoSeRechaza() {
        // Los detectores NSFW a veces se confunden con mucho color piel: comprobamos que no es el caso
        double p = detector.probabilidadInapropiada(ImagenesDePrueba.lisa(800, 600, new Color(225, 180, 150)));
        assertThat(p).isLessThan(DetectorContenidoInapropiado.UMBRAL);
    }

    @Test
    void porEncimaDelUmbralSeRechaza() {
        assertThatThrownBy(() -> DetectorContenidoInapropiado.comprobar(0.95))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("FOTO_INAPROPIADA"));
        assertThatThrownBy(() -> DetectorContenidoInapropiado.comprobar(DetectorContenidoInapropiado.UMBRAL))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void porDebajoDelUmbralSeAcepta() {
        assertThatCode(() -> DetectorContenidoInapropiado.comprobar(0.79)).doesNotThrowAnyException();
        assertThatCode(() -> DetectorContenidoInapropiado.comprobar(0.0)).doesNotThrowAnyException();
    }
}
