package com.jerseys.backend.auth;

import com.jerseys.backend.auth.dto.AuthDtos.LoginRequest;
import com.jerseys.backend.auth.dto.AuthDtos.LoginResponse;
import com.jerseys.backend.auth.dto.AuthDtos.RegistroRequest;
import com.jerseys.backend.auth.dto.AuthDtos.UsuarioResponse;
import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.comun.HashSeguro;
import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.correo.CorreoService;
import com.jerseys.backend.seguridad.JwtService;
import com.jerseys.backend.usuario.NormalizadorEmail;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * SERVICIO: aquí vive la lógica de negocio (las reglas). Los controladores solo
 * reciben la petición y llaman al servicio; los repositorios solo hablan con la BD.
 *
 *   Controlador  →  Servicio  →  Repositorio  →  Base de datos
 */
@Service
public class AuthService {

    private static final Duration VALIDEZ_TOKEN_EMAIL = Duration.ofHours(24);
    /** El enlace para cambiar la contraseña caduca antes: si alguien lo encuentra luego, ya no sirve. */
    private static final Duration VALIDEZ_TOKEN_PASSWORD = Duration.ofHours(1);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final TokenVerificacionRepository tokens;
    private final PasswordEncoder codificador;
    private final JwtService jwtService;
    private final CorreoService correo;
    private final HashSeguro hash;
    private final DetectorCorreoTemporal correosTemporales;
    private final AppProperties props;
    private final Clock reloj;

    // INYECCIÓN DE DEPENDENCIAS: no hacemos "new" de nada. Spring ve el constructor,
    // busca un objeto de cada tipo y nos lo pasa ya creado.
    public AuthService(UsuarioRepository usuarios, TokenVerificacionRepository tokens, PasswordEncoder codificador,
                       JwtService jwtService, CorreoService correo, HashSeguro hash,
                       DetectorCorreoTemporal correosTemporales, AppProperties props, Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codificador = codificador;
        this.jwtService = jwtService;
        this.correo = correo;
        this.hash = hash;
        this.correosTemporales = correosTemporales;
        this.props = props;
        this.reloj = reloj;
    }

    /**
     * @Transactional: todo lo que hace este método en la BD es "todo o nada".
     * Si algo falla a mitad, se deshacen también los cambios anteriores (rollback).
     */
    @Transactional
    public void registrar(RegistroRequest datos, DatosCliente cliente) {
        if (correosTemporales.esTemporal(datos.email())) {
            throw ApiException.peticionInvalida("EMAIL_TEMPORAL",
                    "No se admiten correos temporales. Regístrate con tu correo habitual.");
        }
        String normalizado = NormalizadorEmail.normalizar(datos.email());
        if (usuarios.existsByEmailNormalizado(normalizado)) {
            throw ApiException.conflicto("EMAIL_YA_REGISTRADO", "Ya existe una cuenta con ese email");
        }
        // El CAPTCHA se comprueba antes, en AuthController (fuera de la transacción).

        Usuario usuario = new Usuario(datos.email().trim(), normalizado, datos.nombre().trim(),
                codificador.encode(datos.password()));
        usuario.registrarConsentimiento(props.privacidad().versionActual(), reloj.instant());
        usuario.setIpRegistroHash(hash.hmac(cliente.ip()));
        usuarios.save(usuario);

        enviarCorreoVerificacion(usuario);
    }

    @Transactional
    public void reenviarVerificacion(String email) {
        // Respondemos igual exista o no la cuenta, para no revelar qué emails están registrados.
        usuarios.findByEmailNormalizado(NormalizadorEmail.normalizar(email))
                .filter(u -> !u.isVerificado())
                .ifPresent(u -> {
                    tokens.invalidarPendientes(u, TokenVerificacion.Tipo.VERIFICACION_EMAIL);
                    enviarCorreoVerificacion(u);
                });
    }

    /** Resultado de verificar: el controlador lo usa para redirigir a la página adecuada del front. */
    public enum ResultadoVerificacion { OK, CADUCADO, INVALIDO }

    @Transactional
    public ResultadoVerificacion verificar(String token) {
        return tokens.findByTokenAndTipo(token, TokenVerificacion.Tipo.VERIFICACION_EMAIL)
                .map(t -> {
                    if (!t.esValido(reloj.instant())) {
                        return t.isUsado() && t.getUsuario().isVerificado()
                                ? ResultadoVerificacion.OK // ya estaba verificada: doble clic en el enlace
                                : ResultadoVerificacion.CADUCADO;
                    }
                    t.marcarUsado();
                    t.getUsuario().setVerificado(true);
                    // No hace falta llamar a save(): dentro de una transacción, Hibernate guarda
                    // solo los cambios de las entidades que ha cargado ("dirty checking").
                    return ResultadoVerificacion.OK;
                })
                .orElse(ResultadoVerificacion.INVALIDO);
    }

    /**
     * "He olvidado mi contraseña": manda un enlace para poner una nueva.
     * Respondemos igual exista o no la cuenta (no revelamos qué emails están registrados).
     * A una cuenta bloqueada por el admin no se le manda nada: no podría entrar igualmente.
     */
    @Transactional
    public void olvidePassword(String email) {
        usuarios.findByEmailNormalizado(NormalizadorEmail.normalizar(email))
                .filter(Usuario::isActivo)
                .ifPresent(u -> {
                    // Solo vale el último enlace que se pida
                    tokens.invalidarPendientes(u, TokenVerificacion.Tipo.RECUPERAR_PASSWORD);
                    String token = generarTokenAleatorio();
                    // Guardamos el HMAC del token, no el token: el de verdad solo está en el correo
                    tokens.save(new TokenVerificacion(u, hash.hmac(token), TokenVerificacion.Tipo.RECUPERAR_PASSWORD,
                            reloj.instant().plus(VALIDEZ_TOKEN_PASSWORD)));
                    // Una página del FRONT con el formulario de la nueva contraseña (no una ruta de la API):
                    // si un antivirus del correo "abre" el enlace para revisarlo, no gasta el token.
                    String enlace = props.urlPublica() + "/restablecer-password.html?token=" + token;
                    correo.enviarRecuperacionPassword(u.getEmail(), u.getNombre(), enlace);
                });
    }

    /** Cambia la contraseña con el token del enlace. Cierra todas las sesiones abiertas de esa cuenta. */
    @Transactional
    public void restablecerPassword(String token, String nuevaPassword) {
        TokenVerificacion t = tokens.findByTokenAndTipo(hash.hmac(token), TokenVerificacion.Tipo.RECUPERAR_PASSWORD)
                .filter(encontrado -> !encontrado.isUsado())
                .orElseThrow(() -> ApiException.peticionInvalida("ENLACE_NO_VALIDO",
                        "Este enlace no es válido o ya se ha usado. Pide otro desde «¿Has olvidado tu contraseña?»."));
        if (t.haCaducado(reloj.instant())) {
            throw ApiException.peticionInvalida("ENLACE_CADUCADO",
                    "Este enlace ha caducado (dura 1 hora). Pide otro desde «¿Has olvidado tu contraseña?».");
        }
        Usuario usuario = t.getUsuario();
        if (!usuario.isActivo()) {
            throw ApiException.prohibido("CUENTA_BLOQUEADA", "Tu cuenta está bloqueada");
        }
        t.marcarUsado();
        tokens.invalidarPendientes(usuario, TokenVerificacion.Tipo.RECUPERAR_PASSWORD);
        usuario.cambiarPassword(codificador.encode(nuevaPassword));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest datos) {
        Usuario usuario = usuarios.findByEmailNormalizado(NormalizadorEmail.normalizar(datos.email()))
                .filter(u -> codificador.matches(datos.password(), u.getPasswordHash()))
                // Mismo mensaje si falla el email o la contraseña: no damos pistas a un atacante
                .orElseThrow(() -> ApiException.noAutorizado("CREDENCIALES_INCORRECTAS",
                        "Email o contraseña incorrectos"));

        if (!usuario.isVerificado()) {
            throw ApiException.prohibido("CUENTA_NO_VERIFICADA",
                    "Tienes que confirmar tu email antes de entrar. Revisa tu correo.");
        }
        if (!usuario.isActivo()) {
            throw ApiException.prohibido("CUENTA_BLOQUEADA", "Tu cuenta está bloqueada");
        }
        return new LoginResponse(jwtService.generar(usuario), jwtService.segundosDeValidez(),
                UsuarioResponse.de(usuario));
    }

    private void enviarCorreoVerificacion(Usuario usuario) {
        String token = generarTokenAleatorio();
        tokens.save(new TokenVerificacion(usuario, token, TokenVerificacion.Tipo.VERIFICACION_EMAIL,
                reloj.instant().plus(VALIDEZ_TOKEN_EMAIL)));
        String enlace = props.urlPublica() + "/api/auth/verificar?token=" + token;
        correo.enviarVerificacion(usuario.getEmail(), usuario.getNombre(), enlace);
    }

    /** 32 bytes aleatorios criptográficamente seguros, en Base64 apto para URLs (43 caracteres). */
    private static String generarTokenAleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
