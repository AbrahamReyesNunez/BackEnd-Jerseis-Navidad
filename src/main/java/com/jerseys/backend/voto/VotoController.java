package com.jerseys.backend.voto;

import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.seguridad.UsuarioActual;
import com.jerseys.backend.voto.VotoDtos.MiVotoResponse;
import com.jerseys.backend.voto.VotoDtos.VotarRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/votos")
@Tag(name = "Votos", description = "Votar (una sola vez) y consultar tus votos")
public class VotoController {

    private final VotoService votoService;

    public VotoController(VotoService votoService) {
        this.votoService = votoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envía tus 3 votos de golpe: 3, 2 y 1 puntos a jerseys distintos (no el tuyo)")
    public List<MiVotoResponse> votar(@AuthenticationPrincipal UsuarioActual yo,
                                      @Valid @RequestBody VotarRequest peticion,
                                      HttpServletRequest http) {
        return votoService.votar(yo.id(), peticion, DatosCliente.de(http));
    }

    @GetMapping("/mios")
    @Operation(summary = "Los votos que has dado tú (nunca los de los demás)")
    public List<MiVotoResponse> mios(@AuthenticationPrincipal UsuarioActual yo) {
        return votoService.misVotos(yo.id());
    }
}
