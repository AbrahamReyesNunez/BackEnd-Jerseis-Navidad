package com.jerseys.backend.aviso;

import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.correo.CorreoService;
import com.jerseys.backend.correo.CorreoService.Destinatario;
import com.jerseys.backend.correo.CorreoService.PremioTexto;
import com.jerseys.backend.fase.Configuracion;
import com.jerseys.backend.fase.ConfiguracionRepository;
import com.jerseys.backend.fase.Fase;
import com.jerseys.backend.fase.FaseService;
import com.jerseys.backend.jersey.JerseyRepository;
import com.jerseys.backend.premio.PremioDtos.PremioResponse;
import com.jerseys.backend.premio.ResultadosService;
import com.jerseys.backend.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Avisos por correo a TODOS los participantes (cuentas verificadas y activas) cuando:
 *  1. empieza la votación,
 *  2. se cierra la votación,
 *  3. se publican los ganadores (a los ganadores les llega un correo con su premio).
 *
 * Las fases no "suceden": se calculan comparando la hora con las fechas. Por eso TareaAvisos
 * llama a comprobar() cada minuto, y aquí miramos si estamos en una fase cuyo aviso aún no se ha
 * mandado. La fecha de envío se guarda en la tabla configuracion, así nunca se manda dos veces
 * (ni aunque se reinicie el servidor).
 *
 * Cada aviso solo se manda DURANTE su fase: si el servidor estuviera apagado toda la votación,
 * al encenderlo no mandaría "¡Ya puedes votar!" con la votación ya cerrada.
 */
@Service
public class AvisosService {

    private static final Logger log = LoggerFactory.getLogger(AvisosService.class);

    private final ConfiguracionRepository configuraciones;
    private final UsuarioRepository usuarios;
    private final JerseyRepository jerseys;
    private final ResultadosService resultados;
    private final CorreoService correo;
    private final AppProperties props;
    private final Clock reloj;

    public AvisosService(ConfiguracionRepository configuraciones, UsuarioRepository usuarios,
                         JerseyRepository jerseys, ResultadosService resultados, CorreoService correo,
                         AppProperties props, Clock reloj) {
        this.configuraciones = configuraciones;
        this.usuarios = usuarios;
        this.jerseys = jerseys;
        this.resultados = resultados;
        this.correo = correo;
        this.props = props;
        this.reloj = reloj;
    }

    @Transactional
    public void comprobar() {
        // Bloqueamos la fila: si por lo que sea hubiera dos comprobaciones a la vez, la segunda
        // espera y ya ve el aviso marcado como enviado
        Configuracion c = configuraciones.findBloqueando(Configuracion.ID_UNICO).orElseThrow();
        if (!c.estaCompleta()) {
            return;
        }
        Instant ahora = reloj.instant();
        Fase fase = FaseService.calcular(c, ahora);

        if (fase == Fase.VOTACION && c.getAvisoVotacionEnviadoEn() == null) {
            c.marcarAvisoVotacion(ahora);
            List<Destinatario> todos = participantes();
            log.info("Aviso «empieza la votación» a {} personas", todos.size());
            correo.enviarVotacionAbierta(todos, c.getFinVotacion(), props.urlPublica());
        }

        if ((fase == Fase.DECISION_JURADO || fase == Fase.ESPERA_RESULTADOS)
                && c.getAvisoFinVotacionEnviadoEn() == null) {
            c.marcarAvisoFinVotacion(ahora);
            List<Destinatario> todos = participantes();
            log.info("Aviso «se cierra la votación» a {} personas", todos.size());
            correo.enviarVotacionCerrada(todos, c.getPublicacionResultados(), props.urlPublica());
        }

        if (fase == Fase.RESULTADOS && c.getAvisoResultadosEnviadoEn() == null) {
            c.marcarAvisoResultados(ahora);
            avisarResultados();
        }
    }

    private void avisarResultados() {
        List<PremioResponse> premios = resultados.calcular().premios();

        // Email del autor de cada jersey ganador → nombre del premio que ha ganado
        Map<String, String> premioPorEmail = new HashMap<>();
        for (PremioResponse p : premios) {
            if (p.ganador() != null) {
                jerseys.findById(p.ganador().jerseyId())
                        .map(j -> j.getUsuario().getEmail())
                        .ifPresent(email -> premioPorEmail.put(email, p.nombre()));
            }
        }
        List<PremioTexto> lista = premios.stream()
                .map(p -> new PremioTexto(p.nombre(), p.ganador() == null ? null : p.ganador().autor()))
                .toList();

        List<Destinatario> todos = participantes();
        log.info("Aviso «ya hay ganadores» a {} personas ({} ganadores)", todos.size(), premioPorEmail.size());
        correo.enviarResultados(todos, lista, premioPorEmail, props.urlPublica());
    }

    /** Todas las cuentas que pueden entrar: verificadas, activas y sin borrar. */
    private List<Destinatario> participantes() {
        return usuarios.findByVerificadoTrueAndActivoTrueAndBorradaEnIsNull().stream()
                .map(u -> new Destinatario(u.getEmail(), u.getNombre()))
                .toList();
    }
}
