package com.jerseys.backend.fase;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba las fases con el calendario real del concurso, en hora de España. */
class FaseServiceTest {

    private static final ZoneId ESPANA = ZoneId.of("Europe/Madrid");

    /** Una hora de diciembre de 2026 en España (p. ej. espana(10, 12, 0) = 10 dic a las 12:00). */
    private static Instant espana(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(ESPANA).toInstant();
    }

    private static Configuracion calendarioDelConcurso() {
        Configuracion c = new Configuracion();
        c.cambiarFechas(espana(6, 12, 0), espana(10, 12, 0), espana(10, 12, 0), espana(13, 12, 0),
                espana(14, 12, 0), espana(16, 12, 0));
        return c;
    }

    @Test
    void lasFasesCambianALas12DelMediodiaEnEspana() {
        Configuracion c = calendarioDelConcurso();

        assertThat(FaseService.calcular(c, espana(6, 11, 59))).isEqualTo(Fase.PREPARACION);
        assertThat(FaseService.calcular(c, espana(6, 12, 0))).isEqualTo(Fase.SUBIDA);
        assertThat(FaseService.calcular(c, espana(10, 11, 59))).isEqualTo(Fase.SUBIDA);
        // La subida acaba justo cuando empieza la votación: no hay fase ESPERA entre medias
        assertThat(FaseService.calcular(c, espana(10, 12, 0))).isEqualTo(Fase.VOTACION);
        assertThat(FaseService.calcular(c, espana(13, 11, 59))).isEqualTo(Fase.VOTACION);
        assertThat(FaseService.calcular(c, espana(13, 12, 0))).isEqualTo(Fase.DECISION_JURADO);
        assertThat(FaseService.calcular(c, espana(14, 12, 0))).isEqualTo(Fase.ESPERA_RESULTADOS);
        assertThat(FaseService.calcular(c, espana(16, 11, 59))).isEqualTo(Fase.ESPERA_RESULTADOS);
        assertThat(FaseService.calcular(c, espana(16, 12, 0))).isEqualTo(Fase.RESULTADOS);
    }

    @Test
    void enUtcSonLas11() {
        // Lo que hay que mandar a PUT /api/admin/configuracion (en diciembre España = UTC+1)
        assertThat(espana(6, 12, 0)).isEqualTo(Instant.parse("2026-12-06T11:00:00Z"));
        assertThat(espana(16, 12, 0)).isEqualTo(Instant.parse("2026-12-16T11:00:00Z"));
    }

    @Test
    void sinTodasLasFechasEstaEnPreparacion() {
        Configuracion c = new Configuracion();
        assertThat(FaseService.calcular(c, espana(10, 12, 0))).isEqualTo(Fase.PREPARACION);
    }
}
