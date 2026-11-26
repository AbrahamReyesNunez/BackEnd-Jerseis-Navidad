package com.jerseys.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configura Swagger UI (http://localhost:8080/swagger-ui.html).
 * Añade el botón "Authorize" para pegar el JWT y probar las rutas protegidas.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "API del concurso de jerseys", version = "v1",
                description = "Contrato entre el backend y el frontend. Errores: { codigo, mensaje, campos }"),
        security = @SecurityRequirement(name = "jwt"))
@SecurityScheme(name = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
