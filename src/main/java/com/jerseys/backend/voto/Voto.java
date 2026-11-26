package com.jerseys.backend.voto;

import com.jerseys.backend.jersey.Jersey;
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

@Entity
@Table(name = "votos")
public class Voto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jersey_id")
    private Jersey jersey;

    @Column(nullable = false)
    private int puntos;

    /** POPULAR, o una de las categorías del jurado. Se guarda como texto ("POPULAR", "DIVERTIDO"...). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private Instant fecha;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(nullable = false)
    private boolean anulado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por")
    private Usuario anuladoPor;

    protected Voto() {
    }

    public Voto(Usuario usuario, Jersey jersey, Categoria categoria, int puntos, Instant fecha, String ipHash,
                String userAgent) {
        this.usuario = usuario;
        this.jersey = jersey;
        this.categoria = categoria;
        this.puntos = puntos;
        this.fecha = fecha;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
    }

    public void anular(Usuario admin) {
        this.anulado = true;
        this.anuladoPor = admin;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Jersey getJersey() { return jersey; }
    public int getPuntos() { return puntos; }
    public Categoria getCategoria() { return categoria; }
    public Instant getFecha() { return fecha; }
    public String getIpHash() { return ipHash; }
    public String getUserAgent() { return userAgent; }
    public boolean isAnulado() { return anulado; }
}
