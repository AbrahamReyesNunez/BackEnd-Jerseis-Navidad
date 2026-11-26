package com.jerseys.backend.imagen;

import com.jerseys.backend.comun.ApiException;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Rechaza fotos con contenido sexual o desnudos (NSFW, "Not Safe For Work").
 *
 * Usa open_nsfw de Yahoo: una red neuronal (ResNet-50, 24 MB) que devuelve la probabilidad,
 * de 0 a 1, de que una foto sea inapropiada. La ejecutamos con el módulo DNN de OpenCV,
 * el mismo OpenCV que ya usamos para las caras: no hace falta ninguna librería más.
 *
 * Modelo: src/main/resources/modelos/open_nsfw.prototxt (la "forma" de la red)
 *         src/main/resources/modelos/open_nsfw.caffemodel (lo aprendido)
 *         (Yahoo Inc., licencia BSD-2: ver modelos/LICENCIA-open_nsfw.txt)
 */
@Component
@Order(2)
public class DetectorContenidoInapropiado implements ValidadorImagen {

    private static final Logger log = LoggerFactory.getLogger(DetectorContenidoInapropiado.class);

    /**
     * Yahoo recomienda: por debajo de 0.2 es casi seguro que la foto está bien; por encima de 0.8,
     * casi seguro que no. Usamos 0.8 para no rechazar fotos normales por error: lo que se escape
     * lo puede retirar el admin a mano.
     */
    static final double UMBRAL = 0.8;

    private final Net red;

    public DetectorContenidoInapropiado() {
        OpenCvUtil.cargar();
        this.red = Dnn.readNetFromCaffe(
                OpenCvUtil.copiarATemporal("/modelos/open_nsfw.prototxt").toString(),
                OpenCvUtil.copiarATemporal("/modelos/open_nsfw.caffemodel").toString());
        log.info("Detector de contenido inapropiado (open_nsfw) listo");
    }

    @Override
    public void validar(BufferedImage imagen) {
        comprobar(probabilidadInapropiada(imagen));
    }

    /** Separado de validar() para poder probar la regla sin necesitar una foto inapropiada. */
    static void comprobar(double probabilidad) {
        if (probabilidad >= UMBRAL) {
            log.info("Foto rechazada por contenido inapropiado (probabilidad {})", String.format("%.2f", probabilidad));
            throw ApiException.peticionInvalida("FOTO_INAPROPIADA",
                    "La foto no es apropiada para el concurso. Sube otra foto del jersey.");
        }
    }

    /**
     * De 0 (foto normal) a 1 (seguro que es inapropiada).
     * synchronized: la red de OpenCV no se puede usar desde dos peticiones a la vez.
     */
    synchronized double probabilidadInapropiada(BufferedImage imagen) {
        Mat foto = OpenCvUtil.aMat(imagen);
        Mat reducida = new Mat();
        Mat entrada = null;
        Mat salida = null;
        try {
            // Mismos pasos que el script original de Yahoo (si se prepara distinto, el modelo se equivoca):
            // 1. Reducir a 256x256 (sin respetar la proporción)
            Imgproc.resize(foto, reducida, new Size(256, 256), 0, 0, Imgproc.INTER_LINEAR);
            // 2. Quedarse con el cuadrado central de 224x224
            Mat centro = reducida.submat(new Rect(16, 16, 224, 224));
            // 3. Restar el color medio de las fotos con las que se entrenó (en orden B, G, R)
            entrada = Dnn.blobFromImage(centro, 1.0, new Size(224, 224), new Scalar(104, 117, 123), false, false);

            red.setInput(entrada);
            salida = red.forward("prob");
            // La salida son dos números que suman 1: [apropiada, inapropiada]
            return salida.get(0, 1)[0];
        } finally {
            foto.release();
            reducida.release();
            if (entrada != null) entrada.release();
            if (salida != null) salida.release();
        }
    }
}
