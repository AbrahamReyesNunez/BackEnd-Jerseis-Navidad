package com.jerseys.backend.imagen;

/**
 * INTERFAZ: define QUÉ se puede hacer con las imágenes, no CÓMO.
 *
 * Hay dos implementaciones y Spring elige una según app.imagenes.almacen:
 *  - AlmacenLocal ("local"): guarda en una carpeta del disco. Para desarrollo.
 *  - AlmacenFtp   ("ftp"):   sube al servidor FTP. Para producción.
 *
 * El resto del código solo conoce esta interfaz, así que cambiar de almacén
 * (por ejemplo, a SFTP) es escribir otra clase, sin tocar nada más.
 */
public interface AlmacenImagenes {

    /** Guarda la imagen (ya procesada, en JPG) con ese nombre de archivo. */
    void guardar(String nombreArchivo, byte[] contenido);

    /** Borra la imagen. No falla si ya no existía. */
    void borrar(String nombreArchivo);
}
