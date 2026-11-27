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
import com.jerseys.backend.jersey.JerseyDtos.MisJerseysResponse;
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

    /**
     * Cuántos jerseys activos (no eliminados) puede tener cada usuario en el concurso.
     * Decisión final de la clase: UNO (cada jersey con una o dos fotos, delante y detrás).
     * Si algún día se cambia, basta con este número (y el mensaje de LIMITE_DE_JERSEYS en subir()):
     * el resto del código ya cuenta y bloquea.
     */
    public static final int MAX_JERSEYS_POR_USUARIO = 1;

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

    /**
     * Sube un jersey con su foto de delante (obligatoria) y, si se envía, la de detrás (opcional).
     * Las dos pasan por las mismas comprobaciones (formato, caras, contenido inapropiado).
     */
    @Transactional
    public MiJerseyResponse subir(Long usuarioId, MultipartFile foto, MultipartFile fotoTrasera) {
        fases.exigir("subir jerseys", Fase.SUBIDA);

        // Bloqueamos la fila del usuario hasta el final de la transacción. Si sube dos jerseys a la vez
        // (doble clic, dos pestañas...), la segunda subida espera aquí a que termine la primera, y al
        // contar ya ve el jersey nuevo. Sin esto, las dos contarían "0" y se colarían dos jerseys.
        Usuario usuario = usuarios.findByIdBloqueando(usuarioId)
                .orElseThrow(() -> ApiException.noEncontrado("USUARIO_NO_ENCONTRADO", "Usuario no encontrado"));
        // Los eliminados no cuentan: si el admin te retira el jersey, puedes subir otro
        if (jerseys.countByUsuarioIdAndEstadoNot(usuarioId, EstadoJersey.ELIMINADO) >= MAX_JERSEYS_POR_USUARIO) {
            throw ApiException.conflicto("LIMITE_DE_JERSEYS",
                    "Ya tienes un jersey en el concurso. Solo se permite uno por persona.");
        }

        // Primero se comprueban LAS DOS fotos y solo después se guarda nada: si la de detrás no vale,
        // no queremos haber dejado la de delante subida en el FTP.
        byte[] delante = procesador.procesar(foto);
        byte[] detras = hayArchivo(fotoTrasera) ? procesarTrasera(fotoTrasera) : null;

        // Nombres aleatorios: imposibles de adivinar y sin datos del usuario
        String nombreDelante = UUID.randomUUID() + ".jpg";
        String nombreDetras = detras == null ? null : UUID.randomUUID() + ".jpg";
        List<String> guardadas = new ArrayList<>();
        try {
            almacen.guardar(nombreDelante, delante);
            guardadas.add(nombreDelante);
            if (detras != null) {
                almacen.guardar(nombreDetras, detras);
                guardadas.add(nombreDetras);
            }
            Jersey jersey = jerseys.saveAndFlush(new Jersey(usuario, nombreDelante, nombreDetras, reloj.instant()));
            return aMiJersey(jersey);
        } catch (RuntimeException e) {
            // Si falla algo (el FTP, la BD...), borramos lo que ya se hubiera subido para no dejar basura
            guardadas.forEach(this::borrarSinFallar);
            throw e;
        }
    }

    private static boolean hayArchivo(MultipartFile archivo) {
        return archivo != null && !archivo.isEmpty();
    }

    /** Procesa la foto de detrás. Si no vale, el mensaje lo dice, para que el usuario sepa cuál cambiar. */
    private byte[] procesarTrasera(MultipartFile fotoTrasera) {
        try {
            return procesador.procesar(fotoTrasera);
        } catch (ApiException e) {
            throw new ApiException(e.getEstado(), e.getCodigo(), "Foto de detrás: " + e.getMessage());
        }
    }

    /** Todos los jerseys del usuario (también los eliminados, para que vea el motivo). */
    @Transactional(readOnly = true)
    public MisJerseysResponse misJerseys(Long usuarioId) {
        List<Jersey> lista = jerseys.findByUsuarioIdOrderByFechaSubidaDesc(usuarioId);
        int activos = (int) lista.stream().filter(j -> j.getEstado() != EstadoJersey.ELIMINADO).count();
        return new MisJerseysResponse(MAX_JERSEYS_POR_USUARIO, activos, lista.stream().map(this::aMiJersey).toList());
    }

    /**
     * Lista para votar. Se baraja con una semilla fija por usuario: cada persona ve
     * siempre el mismo orden, pero distinto al de los demás. Así ningún jersey sale
     * beneficiado por aparecer siempre el primero.
     */
    @Transactional(readOnly = true)
    public List<JerseyVotableResponse> listarParaVotar(Long usuarioId) {
        // Después de la votación se pueden seguir viendo (como galería), pero ya no votar
        fases.exigir("ver los jerseys a votar", Fase.VOTACION, Fase.DECISION_JURADO, Fase.ESPERA_RESULTADOS,
                Fase.RESULTADOS);
        List<JerseyVotableResponse> lista = new ArrayList<>(
                jerseys.findByEstadoOrderByFechaSubidaAsc(EstadoJersey.APROBADO).stream()
                        .map(j -> new JerseyVotableResponse(j.getId(), url(j), urlTrasera(j), j.esDe(usuarioId)))
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
        if (jersey.getImagenTraseraNombre() != null) {
            borrarSinFallar(jersey.getImagenTraseraNombre());
        }

        Usuario autor = jersey.getUsuario();
        correo.enviarJerseyEliminado(autor.getEmail(), autor.getNombre(), motivo, fase == Fase.SUBIDA);
        return aAdmin(jersey);
    }

    /**
     * El autor ha borrado su cuenta: se retiran sus jerseys del concurso y se borran sus fotos.
     * Devuelve los que se han retirado (para avisar a quien los hubiera votado).
     */
    @Transactional
    public List<Jersey> retirarPorBajaDelAutor(Long usuarioId) {
        List<Jersey> retirados = jerseys.findByUsuarioIdOrderByFechaSubidaDesc(usuarioId).stream()
                .filter(j -> j.getEstado() != EstadoJersey.ELIMINADO)
                .toList();
        for (Jersey jersey : retirados) {
            jersey.eliminar(jersey.getUsuario(), "El autor ha borrado su cuenta", reloj.instant());
            borrarSinFallar(jersey.getImagenNombre());
            if (jersey.getImagenTraseraNombre() != null) {
                borrarSinFallar(jersey.getImagenTraseraNombre());
            }
        }
        return retirados;
    }

    /** URL pública de la foto de delante. */
    public String url(Jersey jersey) {
        return props.imagenes().urlBase() + jersey.getImagenNombre();
    }

    /** URL pública de la foto de detrás, o null si el jersey no tiene. */
    public String urlTrasera(Jersey jersey) {
        return jersey.getImagenTraseraNombre() == null ? null
                : props.imagenes().urlBase() + jersey.getImagenTraseraNombre();
    }

    private MiJerseyResponse aMiJersey(Jersey j) {
        boolean eliminado = j.getEstado() == EstadoJersey.ELIMINADO; // sus fotos ya se borraron
        return new MiJerseyResponse(j.getId(), eliminado ? null : url(j), eliminado ? null : urlTrasera(j),
                j.getEstado(), j.getMotivoEliminacion(), j.getFechaSubida());
    }

    private JerseyAdminResponse aAdmin(Jersey j) {
        Usuario u = j.getUsuario();
        boolean eliminado = j.getEstado() == EstadoJersey.ELIMINADO;
        return new JerseyAdminResponse(j.getId(), eliminado ? null : url(j), eliminado ? null : urlTrasera(j),
                j.getEstado(), u.getId(), u.getNombre(), u.getEmail(), j.getFechaSubida(),
                j.getMotivoEliminacion(), j.getFechaEliminacion());
    }

    private void borrarSinFallar(String nombre) {
        try {
            almacen.borrar(nombre);
        } catch (RuntimeException e) {
            log.error("No se pudo borrar la imagen {}", nombre, e);
        }
    }
}
