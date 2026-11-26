package com.jerseys.backend.comun;

import com.jerseys.backend.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Calcula HMAC-SHA256 con una clave secreta. Se usa para guardar las IPs
 * (y el DNI, si algún día se pide) sin guardar el dato real.
 *
 * ¿Por qué no un SHA-256 normal? Porque solo hay ~4.000 millones de IPv4:
 * un ordenador calcula el hash de todas en poco tiempo y "deshace" el hash.
 * Con HMAC, sin la clave secreta no se puede.
 */
@Component
public class HashSeguro {

    private final SecretKeySpec clave;

    public HashSeguro(AppProperties props) {
        String secreto = props.hash().secreto();
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("app.hash.secreto debe tener al menos 32 caracteres");
        }
        this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String hmac(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el HMAC", e);
        }
    }
}
