package com.jerseys.backend.premio;

import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * El ganador de una categoría del jurado. Una fila por categoría (la categoría es la clave),
 * y la BD impide que un mismo jersey aparezca en dos filas (ver V6__jurado_elige_ganadores.sql).
 */
@Entity
@Table(name = "ganadores_categoria")
public class GanadorCategoria {

    @Id
    @Enumerated(EnumType.STRING)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jersey_id")
    private Jersey jersey;

    /** Qué miembro del jurado lo marcó (lo deciden juntos, pero uno pulsa el botón). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elegido_por")
    private Usuario elegidoPor;

    @Column(nullable = false)
    private Instant fecha;

    protected GanadorCategoria() {
    }

    public GanadorCategoria(Categoria categoria, Jersey jersey, Usuario elegidoPor, Instant fecha) {
        this.categoria = categoria;
        this.jersey = jersey;
        this.elegidoPor = elegidoPor;
        this.fecha = fecha;
    }

    /** El jurado cambia de opinión: otro jersey para la misma categoría. */
    public void cambiar(Jersey jersey, Usuario elegidoPor, Instant fecha) {
        this.jersey = jersey;
        this.elegidoPor = elegidoPor;
        this.fecha = fecha;
    }

    public Categoria getCategoria() { return categoria; }
    public Jersey getJersey() { return jersey; }
    public Usuario getElegidoPor() { return elegidoPor; }
    public Instant getFecha() { return fecha; }
}
