package com.jerseys.backend.voto;

import com.jerseys.backend.comun.DatosCliente;
import com.jerseys.backend.seguridad.UsuarioActual;
import com.jerseys.backend.voto.VotoDtos.CategoriaJuradoResponse;
import com.jerseys.backend.voto.VotoDtos.MiVotoResponse;
import com.jerseys.backend.voto.VotoDtos.VotarJuradoRequest;
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

/**
 * Votaciones del jurado en las cuatro categorías de premio.
 * Solo rol JURADO: la regla está en SeguridadConfig ("/api/jurado/**"). Cualquier otro recibe 403.
 *
 * El jurado vota en el voto popular por /api/votos, igual que todo el mundo.
 */
@RestController
@RequestMapping("/api/jurado")
@Tag(name = "Jurado", description = "Votos del jurado en las categorías divertido, original, navideño y extravagante")
public class JuradoController {

    private final VotoService votoService;

    public JuradoController(VotoService votoService) {
        this.votoService = votoService;
    }

    @GetMapping("/categorias")
    @Operation(summary = "Las 4 categorías del jurado, con su nombre y lo que ya has votado en cada una")
    public List<CategoriaJuradoResponse> categorias(@AuthenticationPrincipal UsuarioActual yo) {
        return votoService.categoriasDelJurado(yo.id());
    }

    @PostMapping("/votos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Vota en UNA categoría (3, 2 y 1 puntos, como en el voto popular). "
            + "Una vez por categoría y sin poder cambiarlo")
    public List<MiVotoResponse> votar(@AuthenticationPrincipal UsuarioActual yo,
                                      @Valid @RequestBody VotarJuradoRequest peticion,
                                      HttpServletRequest http) {
        return votoService.votarComoJurado(yo.id(), peticion.categoria(), peticion.votos(), DatosCliente.de(http));
    }
}
