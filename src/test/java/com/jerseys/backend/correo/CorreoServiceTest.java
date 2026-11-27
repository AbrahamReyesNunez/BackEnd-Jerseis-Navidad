package com.jerseys.backend.correo;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties.Correo;
import com.jerseys.backend.correo.CorreoService.Destinatario;
import com.jerseys.backend.correo.CorreoService.PremioTexto;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el envío de correos con GreenMail: un servidor SMTP FALSO que arranca dentro del test
 * y se queda con los correos que le llegan, para poder mirarlos. No sale nada a Internet.
 */
class CorreoServiceTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final Correo CONFIG = new Correo("concurso@test.com", "El hilo que nos une", true, Duration.ZERO);

    /** Runnable::run = envía en el mismo hilo, para no tener que esperar en los tests. */
    private CorreoService correo(Correo config, String usuarioSmtp) {
        return new CorreoService(clienteSmtp(ServerSetupTest.SMTP.getPort()), config, usuarioSmtp, Runnable::run,
                Runnable::run);
    }

    private static JavaMailSenderImpl clienteSmtp(int puerto) {
        JavaMailSenderImpl cliente = new JavaMailSenderImpl();
        cliente.setHost("localhost");
        cliente.setPort(puerto);
        return cliente;
    }

    @Test
    void elCorreoDeVerificacionLlegaConTextoYHtml() throws Exception {
        String enlace = "http://localhost:8080/api/auth/verificar?token=abc&x=1";
        correo(CONFIG, "").enviarVerificacion("ana@test.com", "Ana <b>", enlace);

        MimeMessage recibido = smtp.getReceivedMessages()[0];
        InternetAddress de = (InternetAddress) recibido.getFrom()[0];
        assertThat(de.getAddress()).isEqualTo("concurso@test.com");
        assertThat(de.getPersonal()).isEqualTo("El hilo que nos une");
        assertThat(recibido.getSubject()).isEqualTo("Confirma tu cuenta · El hilo que nos une");

        // Versión en texto plano, para programas de correo que no muestran HTML
        assertThat(parte(recibido, "text/plain")).contains(enlace).contains("Hola Ana <b>:");
        // Versión HTML: con el botón, y lo que escribe el usuario escapado (no puede meter HTML)
        String html = parte(recibido, "text/html");
        assertThat(html).contains("href=\"http://localhost:8080/api/auth/verificar?token=abc&amp;x=1\"")
                .contains("Hola Ana &lt;b&gt;:")
                .doesNotContain("Ana <b>");
    }

    @Test
    void elCorreoParaCambiarLaContrasenaLlevaElEnlace() throws Exception {
        String enlace = "http://localhost:8080/restablecer-password.html?token=xyz";
        correo(CONFIG, "").enviarRecuperacionPassword("ana@test.com", "Ana", enlace);

        MimeMessage recibido = smtp.getReceivedMessages()[0];
        assertThat(recibido.getSubject()).isEqualTo("Cambia tu contraseña · El hilo que nos une");
        assertThat(parte(recibido, "text/plain")).contains(enlace).contains("caduca en 1 hora");
        assertThat(parte(recibido, "text/html")).contains("href=\"" + enlace + "\"")
                .contains("Poner una contrase&ntilde;a nueva"); // en HTML la ñ va escapada
    }

    @Test
    void elAvisoDeVotacionLlegaATodosConLaFechaEnHoraDeEspana() throws Exception {
        List<Destinatario> todos = List.of(new Destinatario("ana@test.com", "Ana"),
                new Destinatario("beto@test.com", "Beto"));
        // 13 de diciembre a las 11:00 UTC = a las 12:00 en España
        correo(CONFIG, "").enviarVotacionAbierta(todos, Instant.parse("2026-12-13T11:00:00Z"), "https://web.test");

        MimeMessage[] recibidos = smtp.getReceivedMessages();
        assertThat(recibidos).hasSize(2);
        assertThat(recibidos[0].getSubject()).isEqualTo("¡Ya puedes votar! · El hilo que nos une");
        assertThat(parte(recibidos[0], "text/plain")).contains("Hola Ana:")
                .contains("Tienes hasta el 13 de diciembre a las 12:00.");
        assertThat(parte(recibidos[1], "text/plain")).contains("Hola Beto:");
    }

    @Test
    void losGanadoresRecibenSuEnhorabuenaYLosDemasLaLista() throws Exception {
        List<PremioTexto> premios = List.of(new PremioTexto("El favorito del público", "Beto"),
                new PremioTexto("El más divertido", null));
        correo(CONFIG, "").enviarResultados(
                List.of(new Destinatario("ana@test.com", "Ana"), new Destinatario("beto@test.com", "Beto")),
                premios, Map.of("beto@test.com", "El favorito del público"), "https://web.test");

        MimeMessage paraAna = smtp.getReceivedMessages()[0];
        MimeMessage paraBeto = smtp.getReceivedMessages()[1];
        assertThat(paraAna.getSubject()).isEqualTo("¡Ya tenemos ganadores! · El hilo que nos une");
        assertThat(parte(paraAna, "text/plain"))
                .contains("- El favorito del público: Beto")
                .contains("- El más divertido: Desierto")
                .doesNotContain("ENHORABUENA");
        assertThat(paraBeto.getSubject()).isEqualTo("¡Has ganado «El favorito del público»! · El hilo que nos une");
        assertThat(parte(paraBeto, "text/plain")).contains("¡ENHORABUENA! Tu jersey ha ganado «El favorito del público».");
    }

    @Test
    void elAvisoDeVotoAnuladoDiceLosPuntosYElMotivo() throws Exception {
        correo(CONFIG, "").enviarVotoAnulado("ana@test.com", "Ana", 3, "El jersey se ha retirado del concurso.");

        assertThat(parte(smtp.getReceivedMessages()[0], "text/plain"))
                .contains("Tu voto de 3 puntos ya no cuenta.")
                .contains("Motivo: El jersey se ha retirado del concurso.")
                .contains("puedes volver a dar esos puntos a otro jersey");
    }

    @Test
    void dentroDeUnaTransaccionEsperaAlCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            correo(CONFIG, "").enviarJerseyEliminado("beto@test.com", "Beto", "Foto borrosa", true);
            // Todavía no se ha enviado: la transacción no ha terminado
            assertThat(smtp.getReceivedMessages()).isEmpty();

            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit(); // lo que hace Spring cuando la BD confirma los cambios
            }
            assertThat(smtp.getReceivedMessages()).hasSize(1);
            assertThat(parte(smtp.getReceivedMessages()[0], "text/plain")).contains("Motivo: Foto borrosa");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void siLaTransaccionFallaNoSeEnvia() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            correo(CONFIG, "").enviarVerificacion("ana@test.com", "Ana", "http://enlace");
            // Rollback: Spring NO llama a afterCommit
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }

    @Test
    void sinRemitenteSeUsaLaCuentaSmtp() throws Exception {
        // Gmail exige que el remitente sea la misma cuenta con la que se entra
        correo(new Correo("", "El hilo que nos une", true, Duration.ZERO), "concurso.jerseys@gmail.com")
                .enviarVerificacion("ana@test.com", "Ana", "http://enlace");

        InternetAddress de = (InternetAddress) smtp.getReceivedMessages()[0].getFrom()[0];
        assertThat(de.getAddress()).isEqualTo("concurso.jerseys@gmail.com");
    }

    @Test
    void elCorreoDePruebaLlega() {
        correo(CONFIG, "").enviarPrueba("admin@test.com");
        assertThat(smtp.getReceivedMessages()).hasSize(1);
    }

    @Test
    void siElServidorNoRespondeElCorreoDePruebaDevuelveElError() {
        // Puerto 1: no hay ningún servidor escuchando
        CorreoService roto = new CorreoService(clienteSmtp(1), CONFIG, "", Runnable::run, Runnable::run);

        assertThatThrownBy(() -> roto.enviarPrueba("admin@test.com"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getEstado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCodigo()).isEqualTo("CORREO_NO_ENVIADO");
                });
        // Y un correo normal no rompe la operación del usuario: solo se apunta en el log
        assertThatCode(() -> roto.enviarVerificacion("ana@test.com", "Ana", "http://enlace"))
                .doesNotThrowAnyException();
    }

    @Test
    void enProduccionSinServidorDeCorreoNoArranca() {
        assertThatThrownBy(() -> new CorreoService(null, CONFIG, "", Runnable::run, Runnable::run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_HOST");
    }

    @Test
    void enDesarrolloSinServidorSeEscribeEnLaConsola() {
        CorreoService desarrollo = new CorreoService(null, new Correo("", "El hilo que nos une", false, Duration.ZERO),
                "", Runnable::run, Runnable::run);

        assertThatCode(() -> desarrollo.enviarVerificacion("ana@test.com", "Ana", "http://enlace"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> desarrollo.enviarPrueba("admin@test.com"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CORREO_NO_CONFIGURADO"));
    }

    /** Busca dentro del correo la parte de ese tipo (text/plain o text/html) y devuelve su texto. */
    private static String parte(Part parte, String tipo) throws Exception {
        if (parte.isMimeType(tipo)) {
            return (String) parte.getContent();
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart partes = (Multipart) parte.getContent();
            for (int i = 0; i < partes.getCount(); i++) {
                String encontrada = parte(partes.getBodyPart(i), tipo);
                if (encontrada != null) {
                    return encontrada;
                }
            }
        }
        return null;
    }
}
