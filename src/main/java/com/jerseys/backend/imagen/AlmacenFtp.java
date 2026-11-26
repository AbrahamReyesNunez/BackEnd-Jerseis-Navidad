package com.jerseys.backend.imagen;

import com.jerseys.backend.config.AppProperties;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Sube las fotos al servidor FTP. Solo se activa con app.imagenes.almacen=ftp.
 *
 * Usa FTPS (FTP cifrado) si app.imagenes.ftp.ftps=true. Con FTP normal la
 * contraseña viaja sin cifrar: pedidle a ASIR que habilite FTPS o, mejor, SFTP.
 *
 * TODO (equipo infraestructura): si ASIR monta SFTP, crear AlmacenSftp con
 * Spring Integration SFTP o Apache MINA SSHD y cambiar app.imagenes.almacen.
 */
@Component
@ConditionalOnProperty(name = "app.imagenes.almacen", havingValue = "ftp")
public class AlmacenFtp implements AlmacenImagenes {

    private final AppProperties.Ftp config;

    public AlmacenFtp(AppProperties props) {
        this.config = props.imagenes().ftp();
        if (config == null || config.host() == null || config.host().isBlank()) {
            throw new IllegalStateException("Falta configurar FTP_HOST (o usa IMAGENES_ALMACEN=local)");
        }
    }

    @Override
    public void guardar(String nombreArchivo, byte[] contenido) {
        conConexion(ftp -> {
            if (!ftp.storeFile(nombreArchivo, new ByteArrayInputStream(contenido))) {
                throw new IOException("El FTP rechazó la subida: " + ftp.getReplyString());
            }
        });
    }

    @Override
    public void borrar(String nombreArchivo) {
        conConexion(ftp -> ftp.deleteFile(nombreArchivo)); // devuelve false si no existía: lo ignoramos
    }

    @FunctionalInterface
    private interface OperacionFtp {
        void ejecutar(FTPClient ftp) throws IOException;
    }

    /** Abre la conexión, entra en la carpeta, ejecuta la operación y cierra siempre. */
    private void conConexion(OperacionFtp operacion) {
        FTPClient ftp = config.ftps() ? new FTPSClient() : new FTPClient();
        try {
            ftp.setConnectTimeout(10_000);
            ftp.connect(config.host(), config.puerto());
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new IOException("El servidor FTP rechazó la conexión");
            }
            if (!ftp.login(config.usuario(), config.password())) {
                throw new IOException("Usuario o contraseña del FTP incorrectos");
            }
            if (ftp instanceof FTPSClient ftps) {
                ftps.execPBSZ(0);
                ftps.execPROT("P"); // cifra también los datos, no solo el login
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            if (!ftp.changeWorkingDirectory(config.directorio())) {
                throw new IOException("No existe la carpeta " + config.directorio() + " en el FTP");
            }
            operacion.ejecutar(ftp);
            ftp.logout();
        } catch (IOException e) {
            throw new UncheckedIOException("Error con el servidor FTP", e);
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ignorada) {
                    // nada que hacer
                }
            }
        }
    }
}
