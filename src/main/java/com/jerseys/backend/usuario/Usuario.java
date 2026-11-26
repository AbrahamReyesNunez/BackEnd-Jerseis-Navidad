package com.jerseys.backend.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ENTIDAD JPA: una clase Java que representa una fila de la tabla "usuarios".
 * Hibernate se encarga de convertir entre objetos Usuario y filas de la tabla.
 * Cada atributo corresponde a una columna (ver V1__esquema_inicial.sql).
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // el id lo genera PostgreSQL
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "email_normalizado", nullable = false, unique = true)
    private String emailNormalizado;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING) // se guarda como texto: 'USUARIO', 'PROFESOR', 'ADMIN'
    @Column(nullable = false)
    private Rol rol = Rol.USUARIO;

    @Column(nullable = false)
    private boolean verificado = false;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "ip_registro_hash")
    private String ipRegistroHash;

    @Column(name = "privacidad_aceptada_en", nullable = false)
    private Instant privacidadAceptadaEn;

    @Column(name = "privacidad_version", nullable = false)
    private String privacidadVersion;

    @Column(name = "consentimiento_en", nullable = false)
    private Instant consentimientoEn;

    @Column(name = "mayor_14_confirmado", nullable = false)
    private boolean mayor14Confirmado;

    // JPA necesita un constructor vacío
    protected Usuario() {
    }

    public Usuario(String email, String emailNormalizado, String nombre, String passwordHash) {
        this.email = email;
        this.emailNormalizado = emailNormalizado;
        this.nombre = nombre;
        this.passwordHash = passwordHash;
        this.fechaRegistro = Instant.now();
    }

    /** Guarda la prueba de que el usuario aceptó la política de privacidad y el consentimiento. */
    public void registrarConsentimiento(String versionPolitica, Instant momento) {
        this.privacidadAceptadaEn = momento;
        this.privacidadVersion = versionPolitica;
        this.consentimientoEn = momento;
        this.mayor14Confirmado = true;
    }

    public boolean puedeIniciarSesion() {
        return verificado && activo;
    }

    // ----- Getters y setters -----

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getEmailNormalizado() { return emailNormalizado; }
    public String getNombre() { return nombre; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Instant getFechaRegistro() { return fechaRegistro; }
    public String getIpRegistroHash() { return ipRegistroHash; }
    public void setIpRegistroHash(String ipRegistroHash) { this.ipRegistroHash = ipRegistroHash; }
    public Instant getPrivacidadAceptadaEn() { return privacidadAceptadaEn; }
    public String getPrivacidadVersion() { return privacidadVersion; }
}
