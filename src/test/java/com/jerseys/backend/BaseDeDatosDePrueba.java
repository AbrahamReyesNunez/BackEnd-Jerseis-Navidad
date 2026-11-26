package com.jerseys.backend;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Arranca un PostgreSQL REAL dentro de los tests (sin instalar nada) y se lo da a Spring.
 * Flyway aplica las migraciones sobre él, igual que en producción.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BaseDeDatosDePrueba {

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres postgresEmbebido() throws IOException {
        return EmbeddedPostgres.builder().start();
    }

    @Bean
    public DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
