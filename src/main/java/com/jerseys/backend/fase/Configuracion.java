package com.jerseys.backend.fase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila única (id = 1) con las fechas que marcan las fases del concurso. */
@Entity
@Table(name = "configuracion")
public class Configuracion {

    public static final int ID_UNICO = 1;

    @Id
    private Integer id;

    @Column(name = "inicio_subida")
    private Instant inicioSubida;

    @Column(name = "fin_subida")
    private Instant finSubida;

    @Column(name = "inicio_votacion")
    private Instant inicioVotacion;

    @Column(name = "fin_votacion")
    private Instant finVotacion;

    protected Configuracion() {
    }

    public boolean estaCompleta() {
        return inicioSubida != null && finSubida != null && inicioVotacion != null && finVotacion != null;
    }

    public void cambiarFechas(Instant inicioSubida, Instant finSubida, Instant inicioVotacion, Instant finVotacion) {
        this.inicioSubida = inicioSubida;
        this.finSubida = finSubida;
        this.inicioVotacion = inicioVotacion;
        this.finVotacion = finVotacion;
    }

    public Integer getId() { return id; }
    public Instant getInicioSubida() { return inicioSubida; }
    public Instant getFinSubida() { return finSubida; }
    public Instant getInicioVotacion() { return inicioVotacion; }
    public Instant getFinVotacion() { return finVotacion; }
}
