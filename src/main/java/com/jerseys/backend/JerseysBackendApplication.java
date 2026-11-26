package com.jerseys.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend.
 *
 * @SpringBootApplication hace tres cosas:
 *  1. Busca clases anotadas (@RestController, @Service, @Repository...) en este paquete y subpaquetes.
 *  2. Las crea y las conecta entre sí (inyección de dependencias).
 *  3. Configura automáticamente lo que detecta en el pom.xml (Tomcat, JPA, Flyway, seguridad...).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class JerseysBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(JerseysBackendApplication.class, args);
    }
}
