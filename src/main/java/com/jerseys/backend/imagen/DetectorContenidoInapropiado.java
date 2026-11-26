package com.jerseys.backend.imagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Rechaza fotos con contenido obsceno (NSFW).
 *
 * TODO (equipo validación): implementar con ONNX Runtime (recomendado, local y gratis).
 *  1. Añadir al pom.xml com.microsoft.onnxruntime:onnxruntime.
 *  2. Descargar un modelo NSFW abierto en formato .onnx y ponerlo en src/main/resources/modelos/.
 *  3. Redimensionar la imagen al tamaño que pide el modelo (p. ej. 224x224), normalizar
 *     los píxeles, ejecutar el modelo y leer la probabilidad de "nsfw".
 *  4. Si supera un umbral (p. ej. 0.7):
 *        throw ApiException.peticionInvalida("FOTO_INAPROPIADA", "La foto no es apropiada");
 *  Alternativa: llamar a una API externa (Google Cloud Vision SafeSearch, Sightengine).
 *
 * De momento NO comprueba nada: deja pasar todas las fotos.
 */
@Component
@Order(2)
public class DetectorContenidoInapropiado implements ValidadorImagen {

    private static final Logger log = LoggerFactory.getLogger(DetectorContenidoInapropiado.class);

    @Override
    public void validar(BufferedImage imagen) {
        log.debug("DetectorContenidoInapropiado pendiente de implementar: la foto se acepta sin comprobar");
    }
}
