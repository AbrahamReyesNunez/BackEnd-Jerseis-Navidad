package com.jerseys.backend.auth;

import com.jerseys.backend.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Token de un solo uso que se manda por correo para verificar la cuenta. */
@Entity
@Table(name = "tokens_verificacion")
public class TokenVerificacion {

    public enum Tipo { VERIFICACION_EMAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RELACIÓN: muchos tokens pueden pertenecer a un mismo usuario (columna usuario_id)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private Instant expira;

    @Column(nullable = false)
    private boolean usado = false;

    protected TokenVerificacion() {
    }

    public TokenVerificacion(Usuario usuario, String token, Tipo tipo, Instant expira) {
        this.usuario = usuario;
        this.token = token;
        this.tipo = tipo;
        this.expira = expira;
    }

    public boolean esValido(Instant ahora) {
        return !usado && ahora.isBefore(expira);
    }

    public Usuario getUsuario() { return usuario; }
    public String getToken() { return token; }
    public Tipo getTipo() { return tipo; }
    public boolean isUsado() { return usado; }
    public void marcarUsado() { this.usado = true; }
}
