package com.jerseys.backend.jersey;

import com.jerseys.backend.jersey.JerseyDtos.JerseyVotableResponse;
import com.jerseys.backend.jersey.JerseyDtos.MiJerseyResponse;
import com.jerseys.backend.jersey.JerseyDtos.MisJerseysResponse;
import com.jerseys.backend.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/jerseys")
@Tag(name = "Jerseys", description = "Subir tus jerseys (hasta 3) y ver los del concurso")
public class JerseyController {

    private final JerseyService jerseyService;

    public JerseyController(JerseyService jerseyService) {
        this.jerseyService = jerseyService;
    }

    /**
     * Subida de archivo: el front envía un formulario "multipart/form-data" con un campo "foto".
     * En JS:  const fd = new FormData(); fd.append("foto", input.files[0]);
     *         fetch("/api/jerseys", { method: "POST", headers: { Authorization: "Bearer " + token }, body: fd })
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sube un jersey (solo en fase SUBIDA, hasta 3 por persona; los eliminados no cuentan)")
    public MiJerseyResponse subir(@AuthenticationPrincipal UsuarioActual yo, @RequestPart("foto") MultipartFile foto) {
        return jerseyService.subir(yo.id(), foto);
    }

    @GetMapping("/mios")
    @Operation(summary = "Tus jerseys y su estado, el más reciente primero (lista vacía si no has subido ninguno)")
    public MisJerseysResponse mios(@AuthenticationPrincipal UsuarioActual yo) {
        return jerseyService.misJerseys(yo.id());
    }

    @GetMapping
    @Operation(summary = "Jerseys del concurso para votar (en fase VOTACION). Anónimos; 'esPropio' marca el tuyo")
    public List<JerseyVotableResponse> listar(@AuthenticationPrincipal UsuarioActual yo) {
        return jerseyService.listarParaVotar(yo.id());
    }
}
