package com.jerseys.backend.imagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Rechaza fotos en las que aparece una cara.
 *
 * TODO (equipo validación): implementar con OpenCV.
 *  1. Añadir al pom.xml la dependencia org.openpnp:opencv y cargarla con nu.pattern.OpenCV.loadLocally().
 *  2. Usar el detector YuNet (cv::FaceDetectorYN) o el modelo DNN "res10_300x300_ssd".
 *     NO usar Haar cascades: dan falsos positivos con estampados (renos, muñecos de nieve...).
 *  3. Convertir el BufferedImage a Mat, detectar, y si hay alguna cara con confianza > 0.8:
 *        throw ApiException.peticionInvalida("FOTO_CON_CARA", "La foto no puede mostrar caras");
 *
 * De momento NO comprueba nada: deja pasar todas las fotos.
 */
@Component
@Order(1)
public class DetectorCaras implements ValidadorImagen {

    private static final Logger log = LoggerFactory.getLogger(DetectorCaras.class);

    @Override
    public void validar(BufferedImage imagen) {
        log.debug("DetectorCaras pendiente de implementar: la foto se acepta sin comprobar");
    }
}
