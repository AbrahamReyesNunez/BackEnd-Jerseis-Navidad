package com.jerseys.backend.imagen;

import com.jerseys.backend.comun.ApiException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Convierte la foto que sube el usuario en una foto "segura" para publicar:
 *
 *  1. Comprueba que es de verdad un JPG o PNG (mirando sus primeros bytes, no la extensión).
 *  2. La decodifica, respetando la orientación del móvil.
 *  3. La reduce a un máximo de 1600 px de lado.
 *  4. Pasa todos los ValidadorImagen (caras, contenido inapropiado...).
 *  5. La vuelve a codificar como JPG. Al recodificar se PIERDEN los metadatos EXIF,
 *     que pueden incluir la ubicación GPS de quien hizo la foto.
 */
@Service
public class ProcesadorImagenes {

    private static final int LADO_MAXIMO = 1600;
    private static final int LADO_MINIMO = 200;
    private static final long PIXELES_MAXIMOS = 50_000_000L; // evita imágenes "bomba" que agotan la memoria

    private final List<ValidadorImagen> validadores;

    // Spring inyecta una lista con TODOS los beans que implementan ValidadorImagen
    public ProcesadorImagenes(List<ValidadorImagen> validadores) {
        this.validadores = validadores;
    }

    public byte[] procesar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.peticionInvalida("FOTO_VACIA", "No has enviado ninguna foto");
        }
        try {
            byte[] original = archivo.getBytes();
            if (!esJpg(original) && !esPng(original)) {
                throw ApiException.peticionInvalida("FORMATO_NO_VALIDO", "La foto debe ser JPG o PNG");
            }

            // Leemos solo la cabecera (ancho y alto) ANTES de decodificar la imagen entera
            comprobarDimensiones(original);

            // Thumbnails.of(stream) aplica la orientación EXIF (las fotos de móvil salen derechas)
            BufferedImage imagen = Thumbnails.of(new ByteArrayInputStream(original)).scale(1.0).asBufferedImage();

            if (Math.max(imagen.getWidth(), imagen.getHeight()) > LADO_MAXIMO) {
                imagen = Thumbnails.of(imagen).size(LADO_MAXIMO, LADO_MAXIMO).asBufferedImage();
            }
            imagen = aRgbConFondoBlanco(imagen);

            for (ValidadorImagen validador : validadores) {
                validador.validar(imagen);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Thumbnails.of(imagen).scale(1.0).outputFormat("jpg").outputQuality(0.85).toOutputStream(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw ApiException.peticionInvalida("FOTO_ILEGIBLE", "No se ha podido leer la foto");
        }
    }

    private static void comprobarDimensiones(byte[] original) throws IOException {
        int ancho;
        int alto;
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw ApiException.peticionInvalida("FOTO_ILEGIBLE", "No se ha podido leer la foto");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada);
                ancho = lector.getWidth(0);
                alto = lector.getHeight(0);
            } finally {
                lector.dispose();
            }
        }
        if ((long) ancho * alto > PIXELES_MAXIMOS) {
            throw ApiException.peticionInvalida("FOTO_DEMASIADO_GRANDE", "La foto tiene demasiados píxeles");
        }
        if (Math.min(ancho, alto) < LADO_MINIMO) {
            throw ApiException.peticionInvalida("FOTO_DEMASIADO_PEQUENA",
                    "La foto debe medir al menos " + LADO_MINIMO + " px de lado");
        }
    }

    /** Los PNG pueden tener transparencia; el JPG no. Pintamos la imagen sobre fondo blanco. */
    private static BufferedImage aRgbConFondoBlanco(BufferedImage imagen) {
        BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(imagen, 0, 0, null);
        g.dispose();
        return rgb;
    }

    // "Números mágicos": los primeros bytes que identifican cada formato
    private static boolean esJpg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean esPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }
}
