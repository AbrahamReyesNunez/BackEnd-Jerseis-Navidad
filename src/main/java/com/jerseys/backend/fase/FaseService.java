package com.jerseys.backend.fase;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.fase.FaseDtos.CambiarFechasRequest;
import com.jerseys.backend.fase.FaseDtos.FaseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

/**
 * Calcula en qué fase está el concurso comparando la hora del SERVIDOR con las fechas.
 * Nunca nos fiamos de la hora del navegador del usuario: la podría cambiar.
 */
@Service
public class FaseService {

    private final ConfiguracionRepository configuraciones;
    private final Clock reloj;

    public FaseService(ConfiguracionRepository configuraciones, Clock reloj) {
        this.configuraciones = configuraciones;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Fase faseActual() {
        return calcular(cargar(), reloj.instant());
    }

    /** Lanza un error 403 si el concurso no está en ninguna de las fases indicadas. */
    public Fase exigir(String accion, Fase... permitidas) {
        Fase actual = faseActual();
        if (Arrays.asList(permitidas).contains(actual)) {
            return actual;
        }
        throw ApiException.prohibido("FASE_INCORRECTA",
                "No se puede " + accion + " en la fase actual (" + actual + ")");
    }

    @Transactional(readOnly = true)
    public FaseResponse estado() {
        Configuracion c = cargar();
        Instant ahora = reloj.instant();
        return new FaseResponse(calcular(c, ahora), ahora, c.getInicioSubida(), c.getFinSubida(),
                c.getInicioVotacion(), c.getFinVotacion(), c.getFinJurado(), c.getPublicacionResultados());
    }

    @Transactional
    public FaseResponse cambiarFechas(CambiarFechasRequest f) {
        boolean ordenCorrecto = f.inicioSubida().isBefore(f.finSubida())
                && !f.finSubida().isAfter(f.inicioVotacion())
                && f.inicioVotacion().isBefore(f.finVotacion())
                && f.finVotacion().isBefore(f.finJurado())
                && !f.finJurado().isAfter(f.publicacionResultados());
        if (!ordenCorrecto) {
            throw ApiException.peticionInvalida("FECHAS_INCORRECTAS",
                    "Las fechas deben ir en orden: inicio subida < fin subida <= inicio votación < fin votación "
                            + "< fin jurado <= publicación de resultados");
        }
        cargar().cambiarFechas(f.inicioSubida(), f.finSubida(), f.inicioVotacion(), f.finVotacion(),
                f.finJurado(), f.publicacionResultados());
        return estado();
    }

    private Configuracion cargar() {
        return configuraciones.findById(Configuracion.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de configuración (ver V1__esquema_inicial.sql)"));
    }

    static Fase calcular(Configuracion c, Instant ahora) {
        if (!c.estaCompleta() || ahora.isBefore(c.getInicioSubida())) {
            return Fase.PREPARACION;
        }
        if (ahora.isBefore(c.getFinSubida())) {
            return Fase.SUBIDA;
        }
        if (ahora.isBefore(c.getInicioVotacion())) {
            return Fase.ESPERA;
        }
        if (ahora.isBefore(c.getFinVotacion())) {
            return Fase.VOTACION;
        }
        if (ahora.isBefore(c.getFinJurado())) {
            return Fase.DECISION_JURADO;
        }
        if (ahora.isBefore(c.getPublicacionResultados())) {
            return Fase.ESPERA_RESULTADOS;
        }
        return Fase.RESULTADOS;
    }
}
