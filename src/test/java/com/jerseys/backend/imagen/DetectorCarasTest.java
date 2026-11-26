package com.jerseys.backend.imagen;

import com.jerseys.backend.comun.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test con OpenCV de verdad (sin Spring ni BD).
 * La foto con cara es un retrato de Abraham Lincoln de 1863, de DOMINIO PÚBLICO
 * (Wikimedia Commons: "Abraham_Lincoln_O-77_matte_collodion_print.jpg").
 */
class DetectorCarasTest {

    private static DetectorCaras detector;
    private static BufferedImage retrato;

    @BeforeAll
    static void preparar() throws IOException {
        detector = new DetectorCaras(); // carga OpenCV y el modelo una sola vez para todos los tests
        try (InputStream entrada = DetectorCarasTest.class.getResourceAsStream("/imagenes/retrato-dominio-publico.jpg")) {
            retrato = ImageIO.read(entrada);
        }
    }

    @Test
    void fotoConCaraSeRechaza() {
        assertThat(detector.contarCaras(retrato)).isEqualTo(1);
        assertThatThrownBy(() -> detector.validar(retrato))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("FOTO_CON_CARA"));
    }

    @Test
    void caraPequenaEnUnaEsquinaTambienSeDetecta() {
        // Una foto grande de un jersey con alguien de fondo, pequeñito, en una esquina
        BufferedImage foto = jerseyNavideno(1600, 1200);
        Graphics2D g = foto.createGraphics();
        g.drawImage(retrato, 1350, 50, 200, 258, null);
        g.dispose();

        assertThatThrownBy(() -> detector.validar(foto)).isInstanceOf(ApiException.class);
    }

    @Test
    void jerseyNavidenoSinCarasPasa() {
        // Lleva un muñeco de nieve con ojos y boca: un dibujo no es una cara real
        assertThatCode(() -> detector.validar(jerseyNavideno(1200, 900))).doesNotThrowAnyException();
    }

    @Test
    void fotoLisaPasa() {
        BufferedImage lisa = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        assertThatCode(() -> detector.validar(lisa)).doesNotThrowAnyException();
    }

    /** Dibuja un "jersey" rojo con zigzag, árboles, copos y un muñeco de nieve con cara. */
    private static BufferedImage jerseyNavideno(int ancho, int alto) {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(170, 20, 30));
        g.fillRect(0, 0, ancho, alto);

        // Franja en zigzag
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(12));
        for (int x = 0; x < ancho; x += 60) {
            g.drawLine(x, 120, x + 30, 160);
            g.drawLine(x + 30, 160, x + 60, 120);
        }
        // Árboles
        g.setColor(new Color(20, 110, 40));
        for (int x = 80; x < ancho; x += 260) {
            g.fillPolygon(new int[]{x, x + 60, x + 120}, new int[]{alto - 120, alto - 300, alto - 120}, 3);
        }
        // Copos
        g.setColor(Color.WHITE);
        for (int x = 40; x < ancho; x += 110) {
            g.fillOval(x, 260 + (x % 3) * 40, 18, 18);
        }
        // Muñeco de nieve con cara
        int cx = ancho / 2;
        g.fillOval(cx - 90, 430, 180, 180);
        g.fillOval(cx - 65, 320, 130, 130);
        g.setColor(Color.BLACK);
        g.fillOval(cx - 35, 360, 16, 16);
        g.fillOval(cx + 19, 360, 16, 16);
        g.setStroke(new BasicStroke(5));
        g.drawArc(cx - 30, 375, 60, 40, 200, 140);
        g.setColor(Color.ORANGE);
        g.fillPolygon(new int[]{cx - 6, cx + 6, cx}, new int[]{385, 385, 410}, 3);
        g.dispose();
        return img;
    }
}
