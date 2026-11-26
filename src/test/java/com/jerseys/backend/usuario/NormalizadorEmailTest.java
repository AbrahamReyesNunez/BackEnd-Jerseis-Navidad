package com.jerseys.backend.usuario;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test UNITARIO: prueba una clase sola, sin arrancar Spring ni la base de datos. Muy rápido. */
class NormalizadorEmailTest {

    @Test
    void quitaPuntosYAliasEnGmail() {
        assertThat(NormalizadorEmail.normalizar("Pepe.Garcia+concurso@Gmail.com")).isEqualTo("pepegarcia@gmail.com");
        assertThat(NormalizadorEmail.normalizar("pepegarcia@googlemail.com")).isEqualTo("pepegarcia@gmail.com");
    }

    @Test
    void enOtrosDominiosSoloQuitaElAlias() {
        assertThat(NormalizadorEmail.normalizar(" Ana.Lopez+x@Instituto.es ")).isEqualTo("ana.lopez@instituto.es");
    }
}
