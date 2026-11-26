package com.jerseys.backend.imagen;

import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Utilidades compartidas por los detectores que usan OpenCV (caras, contenido inapropiado). */
final class OpenCvUtil {

    private static boolean cargado = false;

    private OpenCvUtil() {
    }

    /** Carga la parte nativa de OpenCV (C++) que viene dentro del jar. Solo hace falta una vez. */
    static synchronized void cargar() {
        if (!cargado) {
            OpenCV.loadLocally();
            cargado = true;
        }
    }

    /** Convierte la imagen de Java a una matriz de OpenCV, con los colores en orden BGR (azul, verde, rojo). */
    static Mat aMat(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int[] argb = imagen.getRGB(0, 0, ancho, alto, null, 0, ancho);
        byte[] bgr = new byte[ancho * alto * 3];
        for (int i = 0; i < argb.length; i++) {
            bgr[i * 3] = (byte) argb[i];             // azul
            bgr[i * 3 + 1] = (byte) (argb[i] >> 8);  // verde
            bgr[i * 3 + 2] = (byte) (argb[i] >> 16); // rojo
        }
        Mat mat = new Mat(alto, ancho, CvType.CV_8UC3);
        mat.put(0, 0, bgr);
        return mat;
    }

    /**
     * OpenCV necesita los modelos como archivos del disco, y dentro del jar no lo son:
     * los copiamos a la carpeta temporal del sistema.
     */
    static Path copiarATemporal(String recurso) {
        try (InputStream entrada = OpenCvUtil.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new IllegalStateException("No se encuentra " + recurso + " en src/main/resources");
            }
            String nombre = recurso.substring(recurso.lastIndexOf('/') + 1);
            Path temporal = Files.createTempFile("modelo-", "-" + nombre);
            temporal.toFile().deleteOnExit();
            Files.copy(entrada, temporal, StandardCopyOption.REPLACE_EXISTING);
            return temporal;
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido preparar " + recurso, e);
        }
    }
}
