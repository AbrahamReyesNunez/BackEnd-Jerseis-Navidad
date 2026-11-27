package com.jerseys.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arranca la aplicación con los valores por defecto de PRODUCCIÓN (sin SWAGGER_ACTIVADO)
 * y comprueba que no se enseña nada que no deba verse desde Internet.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(BaseDeDatosDePrueba.class)
class ProduccionTest {

    @Autowired
    private WebApplicationContext contexto;

    @Test
    void swaggerEstaApagado() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        // La API sí funciona
        mvc.perform(get("/api/fase")).andExpect(status().isOk());
    }
}
