package com.jerseys.backend.jersey;

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
@Table(name = "jerseys")
public class Jersey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY = el usuario no se carga de la BD hasta que alguien llama a getUsuario()
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Foto de DELANTE (obligatoria). Nombre del archivo en el FTP. */
    @Column(name = "imagen_nombre", nullable = false, unique = true)
    private String imagenNombre;

    /** Foto de DETRÁS (opcional): null si el participante no la subió. */
    @Column(name = "imagen_trasera_nombre", unique = true)
    private String imagenTraseraNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoJersey estado = EstadoJersey.APROBADO;

    @Column(name = "motivo_eliminacion")
    private String motivoEliminacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eliminado_por")
    private Usuario eliminadoPor;

    @Column(name = "fecha_subida", nullable = false)
    private Instant fechaSubida;

    @Column(name = "fecha_eliminacion")
    private Instant fechaEliminacion;

    protected Jersey() {
    }

    public Jersey(Usuario usuario, String imagenNombre, String imagenTraseraNombre, Instant fechaSubida) {
        this.usuario = usuario;
        this.imagenNombre = imagenNombre;
        this.imagenTraseraNombre = imagenTraseraNombre;
        this.fechaSubida = fechaSubida;
    }

    public void eliminar(Usuario admin, String motivo, Instant momento) {
        this.estado = EstadoJersey.ELIMINADO;
        this.eliminadoPor = admin;
        this.motivoEliminacion = motivo;
        this.fechaEliminacion = momento;
    }

    public boolean esDe(Long usuarioId) {
        return usuario.getId().equals(usuarioId);
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public String getImagenNombre() { return imagenNombre; }
    public String getImagenTraseraNombre() { return imagenTraseraNombre; }
    public EstadoJersey getEstado() { return estado; }
    public String getMotivoEliminacion() { return motivoEliminacion; }
    public Instant getFechaSubida() { return fechaSubida; }
    public Instant getFechaEliminacion() { return fechaEliminacion; }
}
