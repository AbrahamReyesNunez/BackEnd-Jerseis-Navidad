package com.jerseys.backend.seguridad;

import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.usuario.Usuario;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Crea y comprueba los JWT (JSON Web Tokens).
 *
 * Un JWT es un texto con tres partes separadas por puntos: cabecera.datos.firma
 *  - "datos" lleva el id del usuario y cuándo caduca (cualquiera puede leerlo: NO metas secretos).
 *  - "firma" se calcula con nuestra clave secreta. Si alguien cambia los datos, la firma
 *    deja de cuadrar y el token se rechaza. Así el servidor no necesita guardar sesiones.
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final Duration duracion;
    private final Clock reloj;

    public JwtService(AppProperties props, Clock reloj) {
        String secreto = props.jwt().secreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secreto debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofHours(props.jwt().expiracionHoras());
        this.reloj = reloj;
    }

    public String generar(Usuario usuario) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }

    public long segundosDeValidez() {
        return duracion.toSeconds();
    }

    /** Devuelve el id del usuario si el token es válido (firma correcta y no caducado). */
    public Optional<Long> validar(String token) {
        try {
            String sujeto = Jwts.parser()
                    .verifyWith(clave)
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.valueOf(sujeto));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
