package com.jerseys.backend.auth;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties.Captcha;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Test UNITARIO: no llama a Cloudflare de verdad. MockRestServiceServer se hace pasar por
 * Cloudflare y contesta lo que le digamos, para probar cada caso sin Internet.
 */
class VerificadorCaptchaTest {

    private final RestClient.Builder clienteHttp = RestClient.builder();
    private final MockRestServiceServer cloudflare = MockRestServiceServer.bindTo(clienteHttp).build();

    private VerificadorCaptcha verificador(boolean activado) {
        return new VerificadorCaptcha(new Captcha(activado, "clave-secreta"), clienteHttp);
    }

    @Test
    void tokenValidoPasa() {
        VerificadorCaptcha v = verificador(true);
        cloudflare.expect(requestTo(VerificadorCaptcha.URL_CLOUDFLARE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("secret", "clave-secreta", "response", "token-bueno")))
                // Respuesta real de Cloudflare (con campos que no usamos, como hostname o metadata)
                .andRespond(withSuccess("""
                        {"challenge_ts":"2026-11-26T08:09:42.559Z","error-codes":[],"hostname":"example.com",
                         "metadata":{"result_with_testing_key":true},"success":true}""", MediaType.APPLICATION_JSON));

        assertThatCode(() -> v.comprobar("token-bueno")).doesNotThrowAnyException();
        cloudflare.verify(); // comprueba que de verdad se llamó a Cloudflare
    }

    @Test
    void tokenRechazadoPorCloudflareDa400() {
        VerificadorCaptcha v = verificador(true);
        cloudflare.expect(requestTo(VerificadorCaptcha.URL_CLOUDFLARE))
                .andRespond(withSuccess("{\"success\": false, \"error-codes\": [\"timeout-or-duplicate\"]}",
                        MediaType.APPLICATION_JSON));

        comprobarError(() -> v.comprobar("token-caducado"), HttpStatus.BAD_REQUEST, "CAPTCHA_INVALIDO");
    }

    @Test
    void sinTokenDa400SinLlamarACloudflare() {
        VerificadorCaptcha v = verificador(true);

        comprobarError(() -> v.comprobar(null), HttpStatus.BAD_REQUEST, "CAPTCHA_OBLIGATORIO");
        comprobarError(() -> v.comprobar("  "), HttpStatus.BAD_REQUEST, "CAPTCHA_OBLIGATORIO");
        cloudflare.verify(); // no se esperaba ninguna llamada
    }

    @Test
    void siCloudflareFallaNoDejaPasar() {
        VerificadorCaptcha v = verificador(true);
        cloudflare.expect(requestTo(VerificadorCaptcha.URL_CLOUDFLARE)).andRespond(withServerError());

        comprobarError(() -> v.comprobar("token"), HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA_NO_DISPONIBLE");
    }

    @Test
    void desactivadoNoCompruebaNada() {
        VerificadorCaptcha v = verificador(false);

        assertThatCode(() -> v.comprobar(null)).doesNotThrowAnyException();
        cloudflare.verify(); // no se esperaba ninguna llamada
    }

    @Test
    void activadoSinClaveNoArranca() {
        assertThatThrownBy(() -> new VerificadorCaptcha(new Captcha(true, ""), clienteHttp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURNSTILE_SECRETO");
    }

    private static void comprobarError(Runnable accion, HttpStatus estado, String codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getEstado()).isEqualTo(estado);
            assertThat(e.getCodigo()).isEqualTo(codigo);
        });
    }
}
