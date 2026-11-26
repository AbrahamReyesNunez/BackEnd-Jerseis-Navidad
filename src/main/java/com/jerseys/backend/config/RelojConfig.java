package com.jerseys.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Un @Bean es un objeto que Spring crea una vez y reparte a quien lo pida.
 *
 * Usamos un Clock en vez de llamar a Instant.now() directamente para que,
 * en los tests, se pueda "viajar en el tiempo" sustituyendo el reloj.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
