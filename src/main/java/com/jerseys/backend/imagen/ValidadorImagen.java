package com.jerseys.backend.imagen;

import java.awt.image.BufferedImage;

/**
 * Una comprobación de contenido sobre la foto ya decodificada.
 * Si la foto no es válida, lanza ApiException con un mensaje para el usuario.
 *
 * Para añadir una comprobación nueva basta con crear una clase @Component que
 * implemente esta interfaz: ProcesadorImagenes las ejecuta TODAS automáticamente.
 */
public interface ValidadorImagen {

    void validar(BufferedImage imagen);
}
