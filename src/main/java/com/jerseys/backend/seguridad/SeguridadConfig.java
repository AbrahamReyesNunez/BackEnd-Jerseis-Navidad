package com.jerseys.backend.seguridad;

import com.jerseys.backend.config.AppProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Las "reglas de la puerta": qué rutas son públicas y cuáles exigen login o un rol.
 *
 * IMPORTANTE: esto es lo que protege de verdad la aplicación. Que el front oculte
 * un botón no protege nada: cualquiera puede llamar a la API con Postman o curl.
 */
@Configuration
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, FiltroJwt filtroJwt) throws Exception {
        http
                // CSRF protege formularios con cookies de sesión. Usamos JWT en cabecera, no cookies: no aplica.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // STATELESS: el servidor no guarda sesiones. Cada petición se identifica con su JWT.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        // ----- Públicas -----
                        .requestMatchers("/api/auth/registro", "/api/auth/login",
                                "/api/auth/verificar", "/api/auth/reenviar-verificacion").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/fase").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Fotos (en desarrollo las sirve Spring; en producción, Nginx)
                        .requestMatchers(HttpMethod.GET, "/imagenes/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // ----- Por rol -----
                        .requestMatchers("/api/ranking/**").hasAnyRole("PROFESOR", "ADMIN")
                        .requestMatchers("/api/jurado/**").hasRole("JURADO")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // ----- Todo lo demás: basta con estar logueado -----
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        // Sin token (o token inválido) en una ruta protegida → 401
                        .authenticationEntryPoint((peticion, respuesta, ex) ->
                                escribirError(respuesta, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                                        "Tienes que iniciar sesión"))
                        // Con token pero sin el rol necesario → 403
                        .accessDeniedHandler((peticion, respuesta, ex) ->
                                escribirError(respuesta, HttpStatus.FORBIDDEN, "SIN_PERMISO",
                                        "No tienes permiso para hacer esto")))
                // Nuestro filtro JWT se ejecuta antes del filtro estándar de usuario/contraseña
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Evita que Spring registre FiltroJwt una segunda vez como filtro "normal"
     * (ya está dentro de la cadena de seguridad).
     */
    @Bean
    public FilterRegistrationBean<FiltroJwt> noRegistrarFiltroJwtDosVeces(FiltroJwt filtro) {
        FilterRegistrationBean<FiltroJwt> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    /** bcrypt: algoritmo lento a propósito para que adivinar contraseñas por fuerza bruta sea caro. */
    @Bean
    public PasswordEncoder codificadorPasswords() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS: el navegador bloquea las llamadas de una web a OTRO dominio/puerto salvo que el
     * servidor lo permita. En producción no hace falta (Nginx sirve todo bajo el mismo dominio);
     * en desarrollo permite al front abierto con Live Server (puerto 5500) llamar al 8080.
     */
    @Bean
    public CorsConfigurationSource configuracionCors(AppProperties props) {
        List<String> origenes = props.cors() == null || props.cors().origenes() == null
                ? List.of()
                : props.cors().origenes().stream().filter(o -> !o.isBlank()).toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }

    private static void escribirError(jakarta.servlet.http.HttpServletResponse respuesta, HttpStatus estado,
                                      String codigo, String mensaje) throws java.io.IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\",\"campos\":null}");
    }
}
