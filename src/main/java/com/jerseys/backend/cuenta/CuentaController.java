package com.jerseys.backend.cuenta;

import com.jerseys.backend.auth.dto.AuthDtos.LoginResponse;
import com.jerseys.backend.auth.dto.AuthDtos.MensajeResponse;
import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.cuenta.CuentaDtos.BorrarCuentaRequest;
import com.jerseys.backend.cuenta.CuentaDtos.CambiarPasswordRequest;
import com.jerseys.backend.seguridad.LimitadorPeticiones;
import com.jerseys.backend.seguridad.LimitadorPeticiones.Accion;
import com.jerseys.backend.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hay que haber iniciado sesión (cualquier rol). Las dos piden la contraseña actual. */
@RestController
@RequestMapping("/api/cuenta")
@Tag(name = "Mi cuenta", description = "Cambiar la contraseña y borrar la cuenta")
public class CuentaController {

    private final CuentaService cuentaService;
    private final LimitadorPeticiones limitador;

    public CuentaController(CuentaService cuentaService, LimitadorPeticiones limitador) {
        this.cuentaService = cuentaService;
        this.limitador = limitador;
    }

    @PostMapping("/cambiar-password")
    @Operation(summary = "Cambia tu contraseña (hay que saber la actual). Cierra tus sesiones en otros "
            + "navegadores y devuelve un token NUEVO: el front debe guardarlo en lugar del anterior")
    public LoginResponse cambiarPassword(@AuthenticationPrincipal UsuarioActual yo,
                                         @Valid @RequestBody CambiarPasswordRequest datos, HttpServletRequest peticion) {
        // Mismo límite que el login: también aquí se podrían probar contraseñas
        limitador.comprobar(Accion.LOGIN, DatosCliente.de(peticion).ip());
        return cuentaService.cambiarPassword(yo.id(), datos.passwordActual(), datos.passwordNueva());
    }

    @PostMapping("/borrar")
    @Operation(summary = "Borra tu cuenta y tus datos personales (con tu contraseña). Hasta que acaba la "
            + "votación también se retiran tu jersey y tus votos; después se quedan, sin tu nombre. No se puede deshacer")
    public MensajeResponse borrar(@AuthenticationPrincipal UsuarioActual yo,
                                  @Valid @RequestBody BorrarCuentaRequest datos, HttpServletRequest peticion) {
        limitador.comprobar(Accion.LOGIN, DatosCliente.de(peticion).ip());
        cuentaService.borrar(yo.id(), datos.password());
        return new MensajeResponse("Tu cuenta y tus datos se han borrado. ¡Gracias por participar!");
    }
}
