package com.jerseys.backend.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test UNITARIO: usa la lista real de src/main/resources/correos-temporales.txt, sin arrancar Spring. */
class DetectorCorreoTemporalTest {

    private final DetectorCorreoTemporal detector = new DetectorCorreoTemporal();

    @Test
    void detectaDominiosTemporales() {
        assertThat(detector.esTemporal("pepe@mailinator.com")).isTrue();
        assertThat(detector.esTemporal("pepe@yopmail.com")).isTrue();
        assertThat(detector.esTemporal("pepe@10minutemail.com")).isTrue();
    }

    @Test
    void noImportanMayusculasNiEspacios() {
        assertThat(detector.esTemporal("  Pepe@MAILINATOR.com ")).isTrue();
    }

    @Test
    void detectaSubdominiosDeUnDominioTemporal() {
        assertThat(detector.esTemporal("pepe@loquesea.mailinator.com")).isTrue();
    }

    @Test
    void aceptaCorreosNormales() {
        assertThat(detector.esTemporal("pepe@gmail.com")).isFalse();
        assertThat(detector.esTemporal("pepe@outlook.com")).isFalse();
        assertThat(detector.esTemporal("pepe@hotmail.com")).isFalse();
        assertThat(detector.esTemporal("ana@instituto.es")).isFalse();
    }

    @Test
    void noSeFijaEnLaParteDeAntesDeLaArroba() {
        assertThat(detector.esTemporal("mailinator.com@gmail.com")).isFalse();
    }
}
