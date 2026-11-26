package com.jerseys.backend.jersey;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.correo.CorreoService;
import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.imagen.AlmacenImagenes;
import com.jerseys.backend.imagen.ProcesadorImagenes;
import com.jerseys.backend.jersey.JerseyDtos.JerseyAdminResponse;
import com.jerseys.backend.jersey.JerseyDtos.JerseyVotableResponse;
import com.jerseys.backend.jersey.JerseyDtos.MiJerseyResponse;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class JerseyService {

    private static final Logger log = LoggerFactory.getLogger(JerseyService.class);

    private final JerseyRepository jerseys;
    private final UsuarioRepository usuarios;
    private final FaseService fases;
    private final ProcesadorImagenes procesador;
    private final AlmacenImagenes almacen;
    private final CorreoService correo;
    private final AppProperties props;
    private final Clock reloj;

    public JerseyService(JerseyRepository jerseys, UsuarioRepository usuarios, FaseService fases,
                         ProcesadorImagenes procesador, AlmacenImagenes almacen, CorreoService correo,
                         AppProperties props, Clock reloj) {
        this.jerseys = jerseys;
        this.usuarios = usuarios;
        this.fases = fases;
        this.procesador = procesador;
        this.almacen = almacen;
        this.correo = correo;
        this.props = props;
        this.reloj = reloj;
    }

    @Transactional
    public MiJerseyResponse subir(Long usuarioId, MultipartFile foto) {
        fases.exigir("subir jerseys", Fase.SUBIDA);
        if (jerseys.findFirstByUsuarioIdAndEstadoNot(usuarioId, EstadoJersey.ELIMINADO).isPresent()) {
            throw ApiException.conflicto("YA_HAS_SUBIDO", "Ya tienes un jersey en el concurso");
        }

        byte[] jpg = procesador.procesar(foto);
        // Nombre aleatorio: imposible de adivinar y sin datos del usuario
        String nombre = UUID.randomUUID() + ".jpg";
        almacen.guardar(nombre, jpg);

        try {
            Usuario usuario = usuarios.getReferenceById(usuarioId);
            Jersey jersey = jerseys.saveAndFlush(new Jersey(usuario, nombre, reloj.instant()));
            return aMiJersey(jersey);
        } catch (RuntimeException e) {
            // Si falla la BD (p. ej. doble clic: el índice único rechaza el segundo),
            // borramos la foto que ya habíamos subido para no dejar basura en el FTP.
            borrarSinFallar(nombre);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public MiJerseyResponse miJersey(Long usuarioId) {
        return jerseys.findFirstByUsuarioIdOrderByFechaSubidaDesc(usuarioId)
                .map(this::aMiJersey)
                .orElseThrow(() -> ApiException.noEncontrado("SIN_JERSEY", "Todavía no has subido ningún jersey"));
    }

    /**
     * Lista para votar. Se baraja con una semilla fija por usuario: cada persona ve
     * siempre el mismo orden, pero distinto al de los demás. Así ningún jersey sale
     * beneficiado por aparecer siempre el primero.
     */
    @Transactional(readOnly = true)
    public List<JerseyVotableResponse> listarParaVotar(Long usuarioId) {
        fases.exigir("ver los jerseys a votar", Fase.VOTACION, Fase.CERRADO);
        List<JerseyVotableResponse> lista = new ArrayList<>(
                jerseys.findByEstadoOrderByFechaSubidaAsc(EstadoJersey.APROBADO).stream()
                        .map(j -> new JerseyVotableResponse(j.getId(), url(j), j.esDe(usuarioId)))
                        .toList());
        Collections.shuffle(lista, new Random(usuarioId));
        return lista;
    }

    @Transactional(readOnly = true)
    public List<JerseyAdminResponse> listarParaAdmin(EstadoJersey estado) {
        List<Jersey> lista = estado == null ? jerseys.findAllConUsuario() : jerseys.findByEstadoConUsuario(estado);
        return lista.stream().map(this::aAdmin).toList();
    }

    @Transactional
    public JerseyAdminResponse eliminar(Long adminId, Long jerseyId, String motivo) {
        Fase fase = fases.exigir("eliminar jerseys", Fase.SUBIDA, Fase.ESPERA);
        Jersey jersey = jerseys.findById(jerseyId)
                .orElseThrow(() -> ApiException.noEncontrado("JERSEY_NO_ENCONTRADO", "No existe ese jersey"));
        if (jersey.getEstado() == EstadoJersey.ELIMINADO) {
            throw ApiException.conflicto("YA_ELIMINADO", "Ese jersey ya estaba eliminado");
        }

        jersey.eliminar(usuarios.getReferenceById(adminId), motivo, reloj.instant());
        borrarSinFallar(jersey.getImagenNombre());

        Usuario autor = jersey.getUsuario();
        correo.enviarJerseyEliminado(autor.getEmail(), autor.getNombre(), motivo, fase == Fase.SUBIDA);
        return aAdmin(jersey);
    }

    public String url(Jersey jersey) {
        return props.imagenes().urlBase() + jersey.getImagenNombre();
    }

    private MiJerseyResponse aMiJersey(Jersey j) {
        String imagen = j.getEstado() == EstadoJersey.ELIMINADO ? null : url(j);
        return new MiJerseyResponse(j.getId(), imagen, j.getEstado(), j.getMotivoEliminacion(), j.getFechaSubida());
    }

    private JerseyAdminResponse aAdmin(Jersey j) {
        Usuario u = j.getUsuario();
        String imagen = j.getEstado() == EstadoJersey.ELIMINADO ? null : url(j);
        return new JerseyAdminResponse(j.getId(), imagen, j.getEstado(), u.getId(), u.getNombre(), u.getEmail(),
                j.getFechaSubida(), j.getMotivoEliminacion(), j.getFechaEliminacion());
    }

    private void borrarSinFallar(String nombre) {
        try {
            almacen.borrar(nombre);
        } catch (RuntimeException e) {
            log.error("No se pudo borrar la imagen {}", nombre, e);
        }
    }
}
