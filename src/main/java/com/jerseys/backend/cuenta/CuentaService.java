package com.jerseys.backend.cuenta;

import com.jerseys.backend.auth.TokenVerificacionRepository;
import com.jerseys.backend.auth.dto.AuthDtos.LoginResponse;
import com.jerseys.backend.auth.dto.AuthDtos.UsuarioResponse;
import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.seguridad.JwtService;
import com.jerseys.backend.usuario.Rol;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import com.jerseys.backend.voto.VotoService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

/** Lo que cada usuario puede hacer con su propia cuenta: cambiar la contraseña y borrarla. */
@Service
public class CuentaService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final TokenVerificacionRepository tokens;
    private final PasswordEncoder codificador;
    private final JwtService jwtService;
    private final JerseyService jerseyService;
    private final VotoService votoService;
    private final FaseService fases;
    private final Clock reloj;

    public CuentaService(UsuarioRepository usuarios, TokenVerificacionRepository tokens, PasswordEncoder codificador,
                         JwtService jwtService, JerseyService jerseyService, VotoService votoService,
                         FaseService fases, Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.codificador = codificador;
        this.jwtService = jwtService;
        this.jerseyService = jerseyService;
        this.votoService = votoService;
        this.fases = fases;
        this.reloj = reloj;
    }

    /**
     * Cambia la contraseña sabiendo la actual. Se cierran las sesiones abiertas en otros sitios,
     * pero no la de quien la cambia: le devolvemos un token nuevo para que siga dentro.
     */
    @Transactional
    public LoginResponse cambiarPassword(Long usuarioId, String actual, String nueva) {
        Usuario usuario = cargar(usuarioId);
        comprobarPassword(usuario, actual);
        usuario.cambiarPassword(codificador.encode(nueva));
        return new LoginResponse(jwtService.generar(usuario), jwtService.segundosDeValidez(),
                UsuarioResponse.de(usuario));
    }

    /**
     * Borra la cuenta (derecho de supresión de la protección de datos). Se borran SIEMPRE los datos
     * personales: email, nombre, contraseña e IP. Con el jersey y los votos depende de la fase:
     *
     *  - Hasta que acaba la votación: se retira el jersey (y se borran sus fotos) y se borran sus votos.
     *    A quien hubiera votado su jersey le avisamos por correo de que ese voto ya no cuenta.
     *  - Después (jurado, espera y resultados): los premios ya se están decidiendo. El jersey y los
     *    votos se quedan, sin nombre ni IP, para que los ganadores no cambien por sorpresa.
     *    El autor aparece como "Cuenta borrada".
     */
    @Transactional
    public void borrar(Long usuarioId, String password) {
        Usuario usuario = usuarios.findByIdBloqueando(usuarioId).orElseThrow(this::noExiste);
        comprobarPassword(usuario, password);
        if (usuario.getRol() == Rol.ADMIN) {
            throw ApiException.prohibido("ADMIN_NO_PUEDE_BORRARSE",
                    "Un administrador no puede borrar su cuenta. Pide a otro admin que te quite el rol antes.");
        }

        boolean premiosEnJuego = fases.faseActual().compareTo(Fase.VOTACION) > 0;
        if (premiosEnJuego) {
            votoService.anonimizarVotosDe(usuarioId);
        } else {
            votoService.borrarVotosDe(usuarioId);
            List<Jersey> retirados = jerseyService.retirarPorBajaDelAutor(usuarioId);
            votoService.anularVotosA(retirados);
        }

        tokens.deleteByUsuario(usuario);
        // Una contraseña aleatoria que nadie conoce: con esta cuenta ya no se puede entrar
        usuario.borrarDatosPersonales(codificador.encode(aleatorio()), reloj.instant());
    }

    private void comprobarPassword(Usuario usuario, String password) {
        if (!codificador.matches(password, usuario.getPasswordHash())) {
            throw ApiException.peticionInvalida("PASSWORD_INCORRECTA", "La contraseña no es correcta");
        }
    }

    private Usuario cargar(Long id) {
        return usuarios.findById(id).orElseThrow(this::noExiste);
    }

    private ApiException noExiste() {
        return ApiException.noEncontrado("USUARIO_NO_ENCONTRADO", "Usuario no encontrado");
    }

    private static String aleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
