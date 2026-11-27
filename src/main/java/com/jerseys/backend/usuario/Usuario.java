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

    @Enumerated(EnumType.STRING) // se guarda como texto: 'USUARIO', 'JURADO', 'ADMIN'
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

    /** El usuario marcó la casilla "tengo 18 años o más" al registrarse. */
    @Column(name = "mayor_18_confirmado", nullable = false)
    private boolean mayor18Confirmado;

    /**
     * Va dentro de cada JWT. Al cambiar la contraseña sube en 1, y los JWT que llevan el número
     * anterior dejan de valer: es como cambiar la cerradura, las llaves viejas ya no abren.
     */
    @Column(name = "version_sesion", nullable = false)
    private int versionSesion = 0;

    /** Cuándo borró el usuario su cuenta (null = no está borrada). Ver borrarDatosPersonales(). */
    @Column(name = "borrada_en")
    private Instant borradaEn;

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

    /**
     * Guarda la prueba de que el usuario aceptó la política de privacidad y el consentimiento,
     * y confirmó ser mayor de 18 (el DTO de registro no deja llegar aquí sin las tres casillas).
     */
    public void registrarConsentimiento(String versionPolitica, Instant momento) {
        this.privacidadAceptadaEn = momento;
        this.privacidadVersion = versionPolitica;
        this.consentimientoEn = momento;
        this.mayor18Confirmado = true;
    }

    /**
     * Nueva contraseña (ya cifrada) y cierre de todas las sesiones abiertas.
     * Si la cuenta no estaba verificada, queda verificada: el enlace llegó a su correo, así que es suyo.
     */
    public void cambiarPassword(String nuevoHash) {
        this.passwordHash = nuevoHash;
        this.versionSesion++;
        this.verificado = true;
    }

    /**
     * "Borrar mi cuenta": se quitan todos los datos personales, pero la fila se queda (con otro
     * email y el nombre "Cuenta borrada") porque hay votos, jerseys y ganadores que apuntan a ella.
     * Con el mismo email se puede volver a crear una cuenta nueva.
     */
    public void borrarDatosPersonales(String hashInservible, Instant momento) {
        this.email = "borrada-" + id + "@cuenta-borrada.invalid";
        this.emailNormalizado = this.email;
        this.nombre = "Cuenta borrada";
        this.passwordHash = hashInservible;
        this.ipRegistroHash = null;
        this.rol = Rol.USUARIO;
        this.verificado = false;
        this.activo = false;
        this.versionSesion++; // cierra todas sus sesiones
        this.borradaEn = momento;
    }

    public boolean estaBorrada() {
        return borradaEn != null;
    }

    public boolean puedeIniciarSesion() {
        return verificado && activo && !estaBorrada();
    }

    // ----- Getters y setters -----

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getEmailNormalizado() { return emailNormalizado; }
    public String getNombre() { return nombre; }
    public int getVersionSesion() { return versionSesion; }
    public Instant getBorradaEn() { return borradaEn; }
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
