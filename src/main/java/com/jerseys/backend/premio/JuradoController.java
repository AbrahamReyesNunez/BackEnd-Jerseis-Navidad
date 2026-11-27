package com.jerseys.backend.premio;

import com.jerseys.backend.premio.PremioDtos.CategoriaJuradoResponse;
import com.jerseys.backend.premio.PremioDtos.ElegirGanadorRequest;
import com.jerseys.backend.premio.PremioDtos.JerseyCandidatoResponse;
import com.jerseys.backend.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pantalla del jurado. Solo rol JURADO: la regla está en SeguridadConfig ("/api/jurado/**").
 * El jurado vota en el voto popular por /api/votos, igual que todo el mundo.
 */
@RestController
@RequestMapping("/api/jurado")
@Tag(name = "Jurado", description = "El jurado elige el ganador de las categorías divertido, original, "
        + "navideño y extravagante (fase DECISION_JURADO)")
public class JuradoController {

    private final JuradoService juradoService;

    public JuradoController(JuradoService juradoService) {
        this.juradoService = juradoService;
    }

    @GetMapping("/categorias")
    @Operation(summary = "Las 4 categorías del jurado, con el ganador elegido en cada una (null si todavía no)")
    public List<CategoriaJuradoResponse> categorias(@AuthenticationPrincipal UsuarioActual yo) {
        return juradoService.categorias(yo.id());
    }

    @GetMapping("/jerseys")
    @Operation(summary = "Jerseys entre los que elegir (sin el ganador del voto popular). "
            + "esPropio: el tuyo, no puedes marcarlo. ganadorDe: ya ha ganado esa categoría y no puede ganar otra")
    public List<JerseyCandidatoResponse> jerseys(@AuthenticationPrincipal UsuarioActual yo) {
        return juradoService.candidatos(yo.id());
    }

    @PutMapping("/ganadores/{categoria}")
    @Operation(summary = "Marca el ganador de una categoría: {\"jerseyId\": 12}. "
            + "Si ya había uno, lo sustituye (se puede cambiar mientras dure la fase)")
    public CategoriaJuradoResponse elegir(@AuthenticationPrincipal UsuarioActual yo, @PathVariable Categoria categoria,
                                          @Valid @RequestBody ElegirGanadorRequest peticion) {
        return juradoService.elegir(yo.id(), categoria, peticion.jerseyId());
    }

    @DeleteMapping("/ganadores/{categoria}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deja la categoría sin ganador (para corregir una equivocación)")
    public void quitar(@PathVariable Categoria categoria) {
        juradoService.quitar(categoria);
    }
}
