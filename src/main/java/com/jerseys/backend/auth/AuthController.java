package com.jerseys.backend.auth;

import com.jerseys.backend.auth.dto.AuthDtos.LoginRequest;
import com.jerseys.backend.auth.dto.AuthDtos.LoginResponse;
import com.jerseys.backend.auth.dto.AuthDtos.MensajeResponse;
import com.jerseys.backend.auth.dto.AuthDtos.ReenviarRequest;
import com.jerseys.backend.auth.dto.AuthDtos.RegistroRequest;
import com.jerseys.backend.auth.dto.AuthDtos.UsuarioResponse;
import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.seguridad.LimitadorPeticiones;
import com.jerseys.backend.seguridad.LimitadorPeticiones.Accion;
import com.jerseys.backend.seguridad.UsuarioActual;
import com.jerseys.backend.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * CONTROLADOR: recibe las peticiones HTTP y devuelve respuestas JSON.
 *
 *  @RestController  → cada método devuelve datos (Spring los convierte a JSON solo).
 *  @RequestMapping  → prefijo común de las rutas de esta clase.
 *  @PostMapping("/login") → este método atiende  POST /api/auth/login
 *  @RequestBody     → convierte el JSON que llega en un objeto Java.
 *  @Valid           → comprueba las anotaciones de validación del DTO antes de entrar.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Registro, verificación de email y login")
public class AuthController {

    private final AuthService authService;
    private final UsuarioRepository usuarios;
    private final AppProperties props;
    private final LimitadorPeticiones limitador;
    private final VerificadorCaptcha captcha;

    public AuthController(AuthService authService, UsuarioRepository usuarios, AppProperties props,
                          LimitadorPeticiones limitador, VerificadorCaptcha captcha) {
        this.authService = authService;
        this.usuarios = usuarios;
        this.props = props;
        this.limitador = limitador;
        this.captcha = captcha;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta (inactiva) y envía el correo de verificación. "
            + "Necesita captchaToken (Cloudflare Turnstile). 429 si hay demasiados intentos")
    public MensajeResponse registro(@Valid @RequestBody RegistroRequest datos, HttpServletRequest peticion) {
        DatosCliente cliente = DatosCliente.de(peticion);
        limitador.comprobar(Accion.REGISTRO, cliente.ip());
        // Fuera de la transacción de registrar(): así no ocupamos una conexión a la BD
        // mientras esperamos a Cloudflare.
        captcha.comprobar(datos.captchaToken());
        authService.registrar(datos, cliente);
        return new MensajeResponse("Cuenta creada. Revisa tu correo para activarla.");
    }

    /**
     * Es el enlace que llega por correo, así que es un GET que abre el navegador.
     * Activa la cuenta y redirige a una página del front con el resultado:
     *   /verificacion.html?resultado=ok | caducado | invalido
     */
    @GetMapping("/verificar")
    @Operation(summary = "Activa la cuenta (enlace del correo) y redirige al front")
    public ResponseEntity<Void> verificar(@RequestParam String token) {
        var resultado = authService.verificar(token);
        URI destino = URI.create(props.urlPublica() + "/verificacion.html?resultado="
                + resultado.name().toLowerCase());
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino.toString()).build();
    }

    @PostMapping("/reenviar-verificacion")
    @Operation(summary = "Reenvía el correo de verificación (si la cuenta existe y no está verificada)")
    public MensajeResponse reenviar(@Valid @RequestBody ReenviarRequest datos, HttpServletRequest peticion) {
        limitador.comprobar(Accion.REENVIAR_VERIFICACION, DatosCliente.de(peticion).ip());
        authService.reenviarVerificacion(datos.email());
        return new MensajeResponse("Si la cuenta existe y no está verificada, te hemos enviado otro correo.");
    }

    @PostMapping("/login")
    @Operation(summary = "Devuelve un JWT para usar en la cabecera Authorization: Bearer <token>. 429 si hay demasiados intentos")
    public LoginResponse login(@Valid @RequestBody LoginRequest datos, HttpServletRequest peticion) {
        limitador.comprobar(Accion.LOGIN, DatosCliente.de(peticion).ip());
        return authService.login(datos);
    }

    @GetMapping("/yo")
    @Operation(summary = "Datos del usuario que ha iniciado sesión")
    public UsuarioResponse yo(@AuthenticationPrincipal UsuarioActual yo) {
        return usuarios.findById(yo.id())
                .map(UsuarioResponse::de)
                .orElseThrow(() -> ApiException.noEncontrado("USUARIO_NO_ENCONTRADO", "Usuario no encontrado"));
    }
}
