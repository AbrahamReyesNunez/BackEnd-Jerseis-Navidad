package com.jerseys.backend.fase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila única (id = 1) con las fechas que marcan las fases del concurso.
 * La decisión del jurado empieza justo al acabar la votación (finVotacion).
 */
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

    @Column(name = "fin_jurado")
    private Instant finJurado;

    @Column(name = "publicacion_resultados")
    private Instant publicacionResultados;

    // Cuándo se mandó cada aviso por correo (null = todavía no). Ver aviso/AvisosService.
    @Column(name = "aviso_votacion_enviado_en")
    private Instant avisoVotacionEnviadoEn;

    @Column(name = "aviso_fin_votacion_enviado_en")
    private Instant avisoFinVotacionEnviadoEn;

    @Column(name = "aviso_resultados_enviado_en")
    private Instant avisoResultadosEnviadoEn;

    protected Configuracion() {
    }

    public boolean estaCompleta() {
        return inicioSubida != null && finSubida != null && inicioVotacion != null && finVotacion != null
                && finJurado != null && publicacionResultados != null;
    }

    public void cambiarFechas(Instant inicioSubida, Instant finSubida, Instant inicioVotacion, Instant finVotacion,
                              Instant finJurado, Instant publicacionResultados) {
        this.inicioSubida = inicioSubida;
        this.finSubida = finSubida;
        this.inicioVotacion = inicioVotacion;
        this.finVotacion = finVotacion;
        this.finJurado = finJurado;
        this.publicacionResultados = publicacionResultados;
    }

    /**
     * Al cambiar las fechas: si el momento de un aviso vuelve a estar en el futuro (p. ej. el admin
     * abrió la votación por error y luego la retrasó), ese aviso se podrá mandar otra vez.
     */
    public void reiniciarAvisosPendientes(Instant ahora) {
        if (inicioVotacion.isAfter(ahora)) {
            avisoVotacionEnviadoEn = null;
        }
        if (finVotacion.isAfter(ahora)) {
            avisoFinVotacionEnviadoEn = null;
        }
        if (publicacionResultados.isAfter(ahora)) {
            avisoResultadosEnviadoEn = null;
        }
    }

    public void marcarAvisoVotacion(Instant momento) { this.avisoVotacionEnviadoEn = momento; }
    public void marcarAvisoFinVotacion(Instant momento) { this.avisoFinVotacionEnviadoEn = momento; }
    public void marcarAvisoResultados(Instant momento) { this.avisoResultadosEnviadoEn = momento; }

    public Integer getId() { return id; }
    public Instant getInicioSubida() { return inicioSubida; }
    public Instant getFinSubida() { return finSubida; }
    public Instant getInicioVotacion() { return inicioVotacion; }
    public Instant getFinVotacion() { return finVotacion; }
    public Instant getFinJurado() { return finJurado; }
    public Instant getPublicacionResultados() { return publicacionResultados; }
    public Instant getAvisoVotacionEnviadoEn() { return avisoVotacionEnviadoEn; }
    public Instant getAvisoFinVotacionEnviadoEn() { return avisoFinVotacionEnviadoEn; }
    public Instant getAvisoResultadosEnviadoEn() { return avisoResultadosEnviadoEn; }
}
