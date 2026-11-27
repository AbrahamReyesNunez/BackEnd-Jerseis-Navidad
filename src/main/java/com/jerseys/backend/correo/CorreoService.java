package com.jerseys.backend.correo;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.config.AppProperties.Correo;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Envía los correos de la aplicación por SMTP (Gmail, Brevo, el correo del centro... ver GUIA.md).
 *
 * Tres detalles importantes:
 *  1. Se envían EN SEGUNDO PLANO: el usuario no espera a que el servidor de correo conteste.
 *  2. Se envían DESPUÉS de que la base de datos confirme los cambios (commit). Si el registro
 *     fallara a mitad, no habríamos mandado un enlace a una cuenta que no existe.
 *  3. Si no hay servidor SMTP (spring.mail.host vacío), el correo se escribe en la consola.
 *     Eso solo se permite si app.correo.obligatorio=false (desarrollo y tests): en producción
 *     la aplicación NO arranca sin correo, porque nadie podría activar su cuenta.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    /** Un correo listo para enviar: versión en texto plano y versión HTML. */
    private record Mensaje(String para, String asunto, String texto, String html) {}

    private final JavaMailSender mail; // null = no hay servidor SMTP: se escribe en la consola
    private final InternetAddress remitente;
    private final Executor envios;

    @Autowired // Spring usa este constructor; el otro es para los tests
    public CorreoService(ObjectProvider<JavaMailSender> proveedor, AppProperties props,
                         @Value("${spring.mail.host:}") String hostSmtp,
                         @Value("${spring.mail.username:}") String usuarioSmtp) {
        this(hostSmtp.isBlank() ? null : proveedor.getIfAvailable(), props.correo(), usuarioSmtp,
                // Dos hilos para enviar: suficiente para un concurso y no satura al servidor de correo
                Executors.newFixedThreadPool(2, tarea -> new Thread(tarea, "envio-correo")));
    }

    CorreoService(JavaMailSender mail, Correo config, String usuarioSmtp, Executor envios) {
        this.mail = mail;
        this.envios = envios;
        if (mail == null) {
            if (config.obligatorio()) {
                throw new IllegalStateException("No hay servidor de correo configurado: define MAIL_HOST, "
                        + "MAIL_USUARIO y MAIL_PASSWORD (ver GUIA.md). Sin correo nadie puede activar su cuenta.");
            }
            log.warn("CORREO DESACTIVADO: los emails se escribirán en la consola (solo para desarrollo)");
            this.remitente = null;
            return;
        }
        String direccion = vacio(config.remitente()) ? usuarioSmtp : config.remitente();
        if (vacio(direccion)) {
            throw new IllegalStateException("Falta la dirección del remitente: define MAIL_REMITENTE o MAIL_USUARIO");
        }
        try {
            this.remitente = new InternetAddress(direccion, config.nombreRemitente(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        log.info("Correo activado: se enviará como {}", this.remitente);
    }

    // ===================== Correos de la aplicación =====================

    public void enviarVerificacion(String destinatario, String nombre, String enlace) {
        enviarDespuesDelCommit(new Mensaje(destinatario, "Confirma tu cuenta · El hilo que nos une",
                """
                Hola %s:

                Gracias por apuntarte al concurso de jerseys navideños «El hilo que nos une».
                Para activar tu cuenta, abre este enlace (caduca en 24 horas):

                %s

                Si no te has registrado tú, ignora este correo.
                """.formatted(nombre, enlace),
                plantilla("¡Ya casi estás!",
                        "<p>Hola " + htmlEscape(nombre) + ":</p>"
                                + "<p>Gracias por apuntarte al concurso de jerseys navideños "
                                + "<strong>«El hilo que nos une»</strong>. Pulsa el botón para activar tu cuenta "
                                + "(el enlace caduca en 24 horas).</p>",
                        "Activar mi cuenta", enlace,
                        "Si no te has registrado tú, ignora este correo.")));
    }

    public void enviarJerseyEliminado(String destinatario, String nombre, String motivo, boolean puedeResubir) {
        String siguientePaso = puedeResubir
                ? "Todavía está abierto el plazo de subida, así que puedes subir otra foto."
                : "El plazo de subida ya ha terminado, así que no podrás subir otra foto.";
        enviarDespuesDelCommit(new Mensaje(destinatario, "Tu jersey ha sido retirado del concurso",
                """
                Hola %s:

                Un administrador ha retirado la foto de tu jersey.
                Motivo: %s

                %s
                """.formatted(nombre, motivo, siguientePaso),
                plantilla("Tu jersey ha sido retirado",
                        "<p>Hola " + htmlEscape(nombre) + ":</p>"
                                + "<p>Un administrador ha retirado la foto de tu jersey.</p>"
                                + "<p><strong>Motivo:</strong> " + htmlEscape(motivo) + "</p>"
                                + "<p>" + siguientePaso + "</p>",
                        null, null, null)));
    }

    /**
     * Correo de prueba para comprobar la configuración (lo usa el admin desde el panel).
     * A diferencia del resto, se envía AHORA y, si falla, devuelve el error en vez de solo apuntarlo.
     */
    public void enviarPrueba(String destinatario) {
        if (mail == null) {
            throw ApiException.servicioNoDisponible("CORREO_NO_CONFIGURADO",
                    "No hay servidor de correo configurado (MAIL_HOST): los correos solo se escriben en la consola");
        }
        Mensaje prueba = new Mensaje(destinatario, "Correo de prueba · El hilo que nos une",
                "Si lees esto, el correo del concurso está bien configurado.",
                plantilla("¡Funciona!", "<p>Si lees esto, el correo del concurso está bien configurado.</p>",
                        null, null, null));
        try {
            enviarAhora(prueba);
        } catch (MailException | MessagingException e) {
            log.error("Falló el correo de prueba", e);
            throw ApiException.servicioNoDisponible("CORREO_NO_ENVIADO",
                    "El servidor de correo ha rechazado el envío: " + e.getMessage());
        }
    }

    // ===================== Cómo se envía =====================

    private void enviarDespuesDelCommit(Mensaje mensaje) {
        Runnable tarea = mail == null
                ? () -> escribirEnConsola(mensaje)
                : () -> envios.execute(() -> enviarSinFallar(mensaje));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Estamos dentro de una transacción (@Transactional): esperamos a que termine bien
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarea.run();
                }
            });
        } else {
            tarea.run();
        }
    }

    /** En segundo plano no hay a quién devolverle el error: lo apuntamos. El usuario puede pedir otro. */
    private void enviarSinFallar(Mensaje mensaje) {
        try {
            enviarAhora(mensaje);
        } catch (MailException | MessagingException e) {
            log.error("No se pudo enviar el correo «{}» a {}", mensaje.asunto(), mensaje.para(), e);
        }
    }

    private void enviarAhora(Mensaje mensaje) throws MessagingException {
        MimeMessage mime = mail.createMimeMessage();
        // true = "multipart": el correo lleva las dos versiones, texto plano y HTML.
        // Cada programa de correo enseña la que sepa mostrar.
        MimeMessageHelper ayudante = new MimeMessageHelper(mime, true, "UTF-8");
        ayudante.setFrom(remitente);
        ayudante.setTo(mensaje.para());
        ayudante.setSubject(mensaje.asunto());
        ayudante.setText(mensaje.texto(), mensaje.html());
        mail.send(mime);
    }

    private static void escribirEnConsola(Mensaje mensaje) {
        log.info("""

                ===== CORREO (no enviado: no hay SMTP configurado) =====
                Para: {}
                Asunto: {}
                {}
                ========================================================""",
                mensaje.para(), mensaje.asunto(), mensaje.texto());
    }

    /**
     * Diseño común de los correos. Los estilos van "en línea" (style="...") porque muchos
     * programas de correo ignoran las hojas de estilo. Todo texto que venga del usuario
     * (nombre, motivo...) se escapa con htmlEscape para que no pueda meter HTML.
     */
    private static String plantilla(String titulo, String cuerpoHtml, String textoBoton, String enlace,
                                    String notaFinal) {
        String boton = enlace == null ? "" : """
                <p style="text-align:center;margin:28px 0">
                  <a href="%s" style="background:#b3122e;color:#ffffff;text-decoration:none;padding:12px 24px;\
                border-radius:6px;font-weight:bold;display:inline-block">%s</a>
                </p>
                <p style="font-size:13px;color:#666">Si el botón no funciona, copia este enlace en el navegador:<br>
                  <a href="%s" style="color:#b3122e;word-break:break-all">%s</a></p>
                """.formatted(htmlEscape(enlace), htmlEscape(textoBoton), htmlEscape(enlace), htmlEscape(enlace));
        String nota = notaFinal == null ? "" : "<p style=\"font-size:13px;color:#666\">" + notaFinal + "</p>";
        return """
                <!DOCTYPE html>
                <html lang="es">
                <body style="margin:0;padding:0;background:#f4f1ec;font-family:Arial,Helvetica,sans-serif;color:#222">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f1ec;padding:24px 0">
                    <tr><td align="center">
                      <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                             style="max-width:560px;width:100%%;background:#ffffff;border-radius:10px;overflow:hidden">
                        <tr><td style="background:#b3122e;color:#ffffff;padding:20px 28px;font-size:20px;font-weight:bold">
                          El hilo que nos une
                        </td></tr>
                        <tr><td style="padding:28px;font-size:15px;line-height:1.5">
                          <h1 style="font-size:22px;margin:0 0 16px">%s</h1>
                          %s
                          %s
                          %s
                        </td></tr>
                        <tr><td style="padding:16px 28px;background:#faf8f5;font-size:12px;color:#888">
                          Concurso de jerseys navideños. Has recibido este correo porque tienes una cuenta en el concurso.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(htmlEscape(titulo), cuerpoHtml, boton, nota);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Al apagar la aplicación, deja terminar los correos que estén a medias. */
    @PreDestroy
    void apagar() {
        if (envios instanceof ExecutorService servicio) {
            servicio.shutdown();
        }
    }
}
