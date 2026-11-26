package com.jerseys.backend.imagen;

import com.jerseys.backend.comun.ApiException;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Rechaza fotos en las que aparece una cara (bases del concurso, apartados 3 y 8: la foto debe
 * centrarse en el jersey y no puede mostrar la cara de ninguna persona).
 *
 * Usa OpenCV con YuNet, una red neuronal pequeña (232 KB) entrenada para encontrar caras REALES.
 * No usamos los antiguos "Haar cascades": confunden con caras los estampados navideños
 * (renos, muñecos de nieve, Papá Noel...).
 *
 * Modelo: src/main/resources/modelos/face_detection_yunet_2023mar.onnx
 *         (opencv_zoo, licencia MIT: ver modelos/LICENCIA-yunet.txt)
 */
@Component
@Order(1)
public class DetectorCaras implements ValidadorImagen {

    private static final Logger log = LoggerFactory.getLogger(DetectorCaras.class);
    private static final String MODELO = "/modelos/face_detection_yunet_2023mar.onnx";

    /** De 0 a 1: cuánta seguridad tiene que tener YuNet para decir "esto es una cara". */
    static final float CONFIANZA_MINIMA = 0.8f;
    /** Si dos recuadros de cara se solapan más que esto, se cuentan como una sola cara. */
    private static final float UMBRAL_SOLAPAMIENTO = 0.3f;
    private static final int MAX_CARAS = 50;

    private final FaceDetectorYN detector;

    public DetectorCaras() {
        OpenCvUtil.cargar();
        Path modelo = OpenCvUtil.copiarATemporal(MODELO);
        // El tamaño (320x320) es provisional: se ajusta al de cada foto antes de detectar
        this.detector = FaceDetectorYN.create(modelo.toString(), "", new Size(320, 320),
                CONFIANZA_MINIMA, UMBRAL_SOLAPAMIENTO, MAX_CARAS);
        log.info("Detector de caras (OpenCV YuNet) listo");
    }

    @Override
    public void validar(BufferedImage imagen) {
        if (contarCaras(imagen) > 0) {
            throw ApiException.peticionInvalida("FOTO_CON_CARA",
                    "En la foto aparece una cara. Haz una foto en la que solo se vea el jersey.");
        }
    }

    /**
     * synchronized: el detector de OpenCV guarda estado (el tamaño de entrada), así que no se puede
     * usar desde dos peticiones a la vez. Las subidas de fotos son pocas: esperar turno no se nota.
     */
    synchronized int contarCaras(BufferedImage imagen) {
        Mat foto = OpenCvUtil.aMat(imagen);
        Mat caras = new Mat();
        try {
            detector.setInputSize(new Size(foto.cols(), foto.rows()));
            detector.detect(foto, caras);
            // Cada FILA de "caras" es una cara: recuadro, ojos, nariz, boca y confianza
            return caras.rows();
        } finally {
            // Los Mat viven en memoria nativa (fuera de Java): hay que liberarlos a mano
            foto.release();
            caras.release();
        }
    }
}
