package com.jerseys.backend.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.config.AppProperties.Captcha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Comprueba el CAPTCHA del registro con Cloudflare Turnstile, para que no se puedan
 * crear cuentas con un programa.
 *
 * Cómo funciona:
 *   1. El FRONT muestra el widget de Turnstile (con la clave PÚBLICA, "sitekey").
 *   2. Cuando la persona lo supera, el widget le da al front un TOKEN de un solo uso.
 *   3. El front manda ese token en el registro, en el campo "captchaToken".
 *   4. El BACKEND (esta clase) le pregunta a Cloudflare, con la clave SECRETA, si el token es bueno.
 *
 * El paso 4 es imprescindible: si solo lo comprobara el front, un script se saltaría el CAPTCHA
 * llamando a la API directamente.
 */
@Component
public class VerificadorCaptcha {

    static final String URL_CLOUDFLARE = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final Logger log = LoggerFactory.getLogger(VerificadorCaptcha.class);

    private final Captcha config;
    private final RestClient http;

    @Autowired // Spring usa este constructor; el otro es para los tests
    public VerificadorCaptcha(AppProperties props) {
        this(props.captcha(), RestClient.builder().requestFactory(conTiempoMaximo(Duration.ofSeconds(5))));
    }

    VerificadorCaptcha(Captcha config, RestClient.Builder clienteHttp) {
        if (config.activado() && (config.secreto() == null || config.secreto().isBlank())) {
            // Mejor no arrancar que arrancar con un CAPTCHA que rechaza a todo el mundo
            throw new IllegalStateException("El CAPTCHA está activado pero falta la clave: define TURNSTILE_SECRETO");
        }
        if (!config.activado()) {
            log.warn("CAPTCHA DESACTIVADO: el registro no comprueba el CAPTCHA (solo para desarrollo)");
        }
        this.config = config;
        this.http = clienteHttp.build();
    }

    /** Lanza un error 400 si el token falta o Cloudflare dice que no es válido. */
    public void comprobar(String token) {
        if (!config.activado()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw ApiException.peticionInvalida("CAPTCHA_OBLIGATORIO",
                    "Completa la verificación de seguridad antes de registrarte.");
        }

        // Cloudflare espera un formulario (como un <form> HTML), no un JSON.
        // No le mandamos la IP del usuario (es opcional): así no compartimos datos personales.
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("secret", config.secreto());
        formulario.add("response", token);

        RespuestaCloudflare respuesta;
        try {
            respuesta = http.post()
                    .uri(URL_CLOUDFLARE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(RespuestaCloudflare.class);
        } catch (RestClientException e) {
            // Cloudflare caído o sin Internet: NO dejamos pasar (si no, bastaría con esperar a un fallo)
            log.error("No se ha podido contactar con Cloudflare Turnstile: {}", e.getMessage());
            throw ApiException.servicioNoDisponible("CAPTCHA_NO_DISPONIBLE",
                    "Ahora mismo no podemos comprobar la verificación de seguridad. Inténtalo en un momento.");
        }

        if (respuesta == null || !respuesta.success()) {
            log.info("CAPTCHA rechazado por Cloudflare: {}", respuesta == null ? "sin respuesta" : respuesta.codigosError());
            throw ApiException.peticionInvalida("CAPTCHA_INVALIDO",
                    "La verificación de seguridad ha fallado o ha caducado. Vuelve a hacerla.");
        }
    }

    /**
     * Lo que contesta Cloudflare, p. ej. {"success": false, "error-codes": ["timeout-or-duplicate"]}.
     * Trae más campos (hostname, challenge_ts...) que no necesitamos: ignoreUnknown los descarta.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaCloudflare(boolean success, @JsonProperty("error-codes") List<String> codigosError) {}

    /** Si Cloudflare tarda más de esto en contestar, se da por fallido (para no dejar al usuario esperando). */
    private static SimpleClientHttpRequestFactory conTiempoMaximo(Duration tiempo) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(tiempo);
        fabrica.setReadTimeout(tiempo);
        return fabrica;
    }
}
