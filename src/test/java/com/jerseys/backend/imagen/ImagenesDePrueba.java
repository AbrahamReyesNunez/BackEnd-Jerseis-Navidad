package com.jerseys.backend.imagen;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Imágenes para los tests de los detectores. */
final class ImagenesDePrueba {

    private ImagenesDePrueba() {
    }

    /**
     * Retrato de Abraham Lincoln (1863), de DOMINIO PÚBLICO
     * (Wikimedia Commons: "Abraham_Lincoln_O-77_matte_collodion_print.jpg").
     */
    static BufferedImage retrato() {
        try (InputStream entrada = ImagenesDePrueba.class.getResourceAsStream("/imagenes/retrato-dominio-publico.jpg")) {
            return ImageIO.read(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Una imagen de un solo color. */
    static BufferedImage lisa(int ancho, int alto, Color color) {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        return img;
    }

    /** Dibuja un "jersey" rojo con zigzag, árboles, copos y un muñeco de nieve con cara. */
    static BufferedImage jerseyNavideno(int ancho, int alto) {
        BufferedImage img = lisa(ancho, alto, new Color(170, 20, 30));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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
