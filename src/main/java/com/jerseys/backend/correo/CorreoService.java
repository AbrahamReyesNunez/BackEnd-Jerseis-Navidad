package com.jerseys.backend.correo;

import com.jerseys.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envía los correos de la aplicación.
 *
 * Si no hay servidor SMTP configurado (spring.mail.host vacío, lo normal en desarrollo),
 * el correo NO se envía: se escribe en la consola para que puedas copiar el enlace.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    private final ObjectProvider<JavaMailSender> remitente;
    private final AppProperties props;
    private final boolean smtpConfigurado;

    public CorreoService(ObjectProvider<JavaMailSender> remitente, AppProperties props,
                         @Value("${spring.mail.host:}") String hostSmtp) {
        this.remitente = remitente;
        this.props = props;
        this.smtpConfigurado = !hostSmtp.isBlank();
    }

    public void enviarVerificacion(String destinatario, String nombre, String enlace) {
        enviar(destinatario, "Confirma tu cuenta del concurso de jerseys", """
                Hola %s:

                Para activar tu cuenta, abre este enlace (caduca en 24 horas):
                %s

                Si no te has registrado tú, ignora este correo.
                """.formatted(nombre, enlace));
    }

    public void enviarJerseyEliminado(String destinatario, String nombre, String motivo, boolean puedeResubir) {
        String siguientePaso = puedeResubir
                ? "Todavía está abierto el plazo de subida, así que puedes subir otra foto."
                : "El plazo de subida ya ha terminado, así que no podrás subir otra foto.";
        enviar(destinatario, "Tu jersey ha sido retirado del concurso", """
                Hola %s:

                Un administrador ha retirado la foto de tu jersey.
                Motivo: %s

                %s
                """.formatted(nombre, motivo, siguientePaso));
    }

    private void enviar(String destinatario, String asunto, String texto) {
        JavaMailSender mail = smtpConfigurado ? remitente.getIfAvailable() : null;
        if (mail == null) {
            log.info("""

                    ===== CORREO (no enviado: no hay SMTP configurado) =====
                    Para: {}
                    Asunto: {}
                    {}
                    ========================================================""", destinatario, asunto, texto);
            return;
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(props.correo().remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        try {
            mail.send(mensaje);
        } catch (MailException e) {
            // No rompemos la operación del usuario si falla el correo: se puede reenviar.
            log.error("No se pudo enviar el correo a {}", destinatario, e);
        }
    }
}
