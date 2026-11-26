package com.jerseys.backend.admin;

import com.jerseys.backend.admin.AdminDtos.CambiarActivoRequest;
import com.jerseys.backend.admin.AdminDtos.CambiarRolRequest;
import com.jerseys.backend.admin.AdminDtos.UsuarioAdminResponse;
import com.jerseys.backend.admin.SospechososDtos.PanelSospechososResponse;
import com.jerseys.backend.fase.FaseDtos.CambiarFechasRequest;
import com.jerseys.backend.fase.FaseDtos.FaseResponse;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.EstadoJersey;
import com.jerseys.backend.jersey.JerseyDtos.EliminarJerseyRequest;
import com.jerseys.backend.jersey.JerseyDtos.JerseyAdminResponse;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.seguridad.UsuarioActual;
import com.jerseys.backend.voto.VotoDtos.VotoAdminResponse;
import com.jerseys.backend.voto.VotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Todo /api/admin/** exige rol ADMIN (regla en SeguridadConfig). */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Gestión de usuarios, jerseys, votos y fechas (solo ADMIN)")
public class AdminController {

    private final AdminUsuariosService adminUsuarios;
    private final JerseyService jerseyService;
    private final VotoService votoService;
    private final FaseService faseService;
    private final PanelSospechososService panelSospechosos;

    public AdminController(AdminUsuariosService adminUsuarios, JerseyService jerseyService,
                           VotoService votoService, FaseService faseService,
                           PanelSospechososService panelSospechosos) {
        this.adminUsuarios = adminUsuarios;
        this.jerseyService = jerseyService;
        this.votoService = votoService;
        this.faseService = faseService;
        this.panelSospechosos = panelSospechosos;
    }

    // ----- Usuarios -----

    @GetMapping("/usuarios")
    @Operation(summary = "Lista de usuarios (opcional: ?buscar=texto en email o nombre)")
    public List<UsuarioAdminResponse> usuarios(@RequestParam(required = false) String buscar) {
        return adminUsuarios.buscar(buscar);
    }

    @PatchMapping("/usuarios/{id}/rol")
    @Operation(summary = "Cambia el rol (p. ej. hacer PROFESOR)")
    public UsuarioAdminResponse cambiarRol(@AuthenticationPrincipal UsuarioActual yo, @PathVariable Long id,
                                           @Valid @RequestBody CambiarRolRequest peticion) {
        return adminUsuarios.cambiarRol(yo.id(), id, peticion.rol());
    }

    @PatchMapping("/usuarios/{id}/activo")
    @Operation(summary = "Bloquea (activo=false) o desbloquea una cuenta")
    public UsuarioAdminResponse cambiarActivo(@AuthenticationPrincipal UsuarioActual yo, @PathVariable Long id,
                                              @Valid @RequestBody CambiarActivoRequest peticion) {
        return adminUsuarios.cambiarActivo(yo.id(), id, peticion.activo());
    }

    // ----- Jerseys -----

    @GetMapping("/jerseys")
    @Operation(summary = "Todos los jerseys con su autor (opcional: ?estado=APROBADO|ELIMINADO)")
    public List<JerseyAdminResponse> jerseys(@RequestParam(required = false) EstadoJersey estado) {
        return jerseyService.listarParaAdmin(estado);
    }

    @PostMapping("/jerseys/{id}/eliminar")
    @Operation(summary = "Retira un jersey (solo en SUBIDA o ESPERA) y avisa al autor por correo")
    public JerseyAdminResponse eliminarJersey(@AuthenticationPrincipal UsuarioActual yo, @PathVariable Long id,
                                              @Valid @RequestBody EliminarJerseyRequest peticion) {
        return jerseyService.eliminar(yo.id(), id, peticion.motivo());
    }

    // ----- Votos -----

    @GetMapping("/votos")
    @Operation(summary = "Todos los votos, con hash de IP y navegador, para detectar fraude")
    public List<VotoAdminResponse> votos() {
        return votoService.listarParaAdmin();
    }

    @PatchMapping("/votos/{id}/anular")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anula un voto fraudulento (deja de contar en el ranking)")
    public void anularVoto(@AuthenticationPrincipal UsuarioActual yo, @PathVariable Long id) {
        votoService.anular(yo.id(), id);
    }

    // ----- Antifraude -----

    @GetMapping("/sospechosos")
    @Operation(summary = "Señales de trampa: ráfagas de registros o votos desde una IP, cuentas de una IP "
            + "que dan 3 puntos al mismo jersey y papeletas idénticas. Son pistas, no pruebas: revisa cada caso")
    public PanelSospechososResponse sospechosos() {
        return panelSospechosos.analizar();
    }

    // ----- Fechas del concurso -----

    @GetMapping("/configuracion")
    @Operation(summary = "Fechas actuales de las fases")
    public FaseResponse configuracion() {
        return faseService.estado();
    }

    @PutMapping("/configuracion")
    @Operation(summary = "Cambia las fechas de las fases (formato ISO, p. ej. 2026-12-01T08:00:00Z)")
    public FaseResponse cambiarConfiguracion(@Valid @RequestBody CambiarFechasRequest peticion) {
        return faseService.cambiarFechas(peticion);
    }
}
