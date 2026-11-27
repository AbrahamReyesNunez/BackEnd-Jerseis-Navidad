package com.jerseys.backend.aviso;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tarea programada: cada minuto le pide a AvisosService que mire si toca mandar algún aviso.
 *
 * En los tests está desactivada (app.avisos.automaticos=false): los tests cambian las fechas
 * todo el rato y llaman a AvisosService.comprobar() a mano cuando quieren.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.avisos.automaticos", havingValue = "true", matchIfMissing = true)
public class TareaAvisos {

    private final AvisosService avisos;

    public TareaAvisos(AvisosService avisos) {
        this.avisos = avisos;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000) // la primera a los 30 s de arrancar, luego cada minuto
    public void comprobar() {
        avisos.comprobar();
    }
}
