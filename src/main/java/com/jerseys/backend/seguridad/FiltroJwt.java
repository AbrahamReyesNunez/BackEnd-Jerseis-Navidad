package com.jerseys.backend.seguridad;

import com.jerseys.backend.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * FILTRO: código que se ejecuta ANTES de llegar a cualquier controlador, en cada petición.
 *
 * Mira si la petición trae la cabecera  "Authorization: Bearer <token>".
 * Si el token es válido, le dice a Spring Security quién es el usuario y qué rol tiene.
 * Si no trae token (o es inválido), no hace nada: luego SeguridadConfig decide
 * si esa ruta era pública o hay que responder 401.
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarios;

    public FiltroJwt(JwtService jwtService, UsuarioRepository usuarios) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {

        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            String token = cabecera.substring(PREFIJO.length());

            jwtService.validar(token)
                    // Consultamos la BD en cada petición: así, si el admin bloquea una cuenta
                    // o cambia un rol, el cambio se aplica al momento (sin esperar a que caduque el token).
                    .flatMap(t -> usuarios.findById(t.usuarioId())
                            // Si ha cambiado la contraseña después de crear este token, ya no vale
                            .filter(u -> u.getVersionSesion() == t.versionSesion()))
                    .filter(u -> u.puedeIniciarSesion())
                    .ifPresent(u -> {
                        var principal = new UsuarioActual(u.getId(), u.getEmail(), u.getRol());
                        // Spring Security usa el prefijo "ROLE_" para los roles
                        var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name()));
                        var autenticacion = new UsernamePasswordAuthenticationToken(principal, null, permisos);
                        SecurityContextHolder.getContext().setAuthentication(autenticacion);
                    });
        }
        // Pasa la petición al siguiente filtro (y al final, al controlador)
        cadena.doFilter(peticion, respuesta);
    }
}
