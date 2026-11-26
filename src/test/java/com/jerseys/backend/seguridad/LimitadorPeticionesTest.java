package com.jerseys.backend.seguridad;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties.Limite;
import com.jerseys.backend.config.AppProperties.Limites;
import com.jerseys.backend.seguridad.LimitadorPeticiones.Accion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test UNITARIO con límites pequeños (3 por hora) para no tener que hacer cientos de peticiones. */
class LimitadorPeticionesTest {

    private final Limite tresPorHora = new Limite(3, Duration.ofHours(1));
    private final LimitadorPeticiones limitador =
            new LimitadorPeticiones(new Limites(tresPorHora, tresPorHora, tresPorHora));

    @Test
    void dejaPasarHastaElLimiteYLuegoDa429() {
        for (int i = 0; i < 3; i++) {
            limitador.comprobar(Accion.LOGIN, "1.1.1.1");
        }
        assertThatThrownBy(() -> limitador.comprobar(Accion.LOGIN, "1.1.1.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getEstado()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCodigo()).isEqualTo("DEMASIADAS_PETICIONES");
                    assertThat(e.getMessage()).contains("Espera");
                });
    }

    @Test
    void cadaIpTieneSuPropioCubo() {
        for (int i = 0; i < 3; i++) {
            limitador.comprobar(Accion.LOGIN, "1.1.1.1");
        }
        assertThatCode(() -> limitador.comprobar(Accion.LOGIN, "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void cadaAccionTieneSuPropioCubo() {
        for (int i = 0; i < 3; i++) {
            limitador.comprobar(Accion.LOGIN, "1.1.1.1");
        }
        // Gastar todos los intentos de login no impide registrarse
        assertThatCode(() -> limitador.comprobar(Accion.REGISTRO, "1.1.1.1")).doesNotThrowAnyException();
    }
}
