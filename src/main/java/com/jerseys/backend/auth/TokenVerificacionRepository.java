package com.jerseys.backend.auth;

import com.jerseys.backend.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, Long> {

    Optional<TokenVerificacion> findByTokenAndTipo(String token, TokenVerificacion.Tipo tipo);

    /** El token más reciente de un usuario (lo usan los tests en lugar de leer el correo). */
    Optional<TokenVerificacion> findFirstByUsuarioEmailOrderByIdDesc(String email);

    /** Invalida los tokens de ese tipo que el usuario tenga sin usar (al pedir otro correo, o al usar uno). */
    @Modifying
    @Query("""
            UPDATE TokenVerificacion t SET t.usado = true
            WHERE t.usuario = :usuario AND t.tipo = :tipo AND t.usado = false
            """)
    void invalidarPendientes(Usuario usuario, TokenVerificacion.Tipo tipo);
}
