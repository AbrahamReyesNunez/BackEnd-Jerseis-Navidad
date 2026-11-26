package com.jerseys.backend.imagen;

import com.jerseys.backend.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guarda las fotos en una carpeta local. Solo se activa con app.imagenes.almacen=local. */
@Component
@ConditionalOnProperty(name = "app.imagenes.almacen", havingValue = "local")
public class AlmacenLocal implements AlmacenImagenes {

    private final Path carpeta;

    public AlmacenLocal(AppProperties props) throws IOException {
        this.carpeta = Path.of(props.imagenes().directorioLocal()).toAbsolutePath().normalize();
        Files.createDirectories(carpeta);
    }

    @Override
    public void guardar(String nombreArchivo, byte[] contenido) {
        try {
            Files.write(resolver(nombreArchivo), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
    }

    @Override
    public void borrar(String nombreArchivo) {
        try {
            Files.deleteIfExists(resolver(nombreArchivo));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar la imagen", e);
        }
    }

    public Path getCarpeta() {
        return carpeta;
    }

    /** Evita "path traversal": que un nombre como "../../algo" escape de la carpeta. */
    private Path resolver(String nombreArchivo) {
        Path ruta = carpeta.resolve(nombreArchivo).normalize();
        if (!ruta.startsWith(carpeta)) {
            throw new IllegalArgumentException("Nombre de archivo no permitido");
        }
        return ruta;
    }
}
