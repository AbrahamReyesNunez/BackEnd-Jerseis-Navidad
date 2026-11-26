package com.jerseys.backend.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atrapa las excepciones que salen de los controladores y las convierte en JSON.
 * Así ningún controlador necesita try/catch, y todos los errores tienen el mismo formato.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorRespuesta> negocio(ApiException e) {
        return ResponseEntity.status(e.getEstado()).body(new ErrorRespuesta(e.getCodigo(), e.getMessage()));
    }

    /** Falla una anotación de validación (@NotBlank, @Email...) de un DTO con @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> campos.putIfAbsent(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("DATOS_INVALIDOS", "Revisa los datos enviados", campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonMalFormado(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("JSON_INVALIDO", "El cuerpo de la petición no es un JSON válido"));
    }

    /** Un parámetro de la URL con un valor que no vale, p. ej. /api/ranking?categoria=NOEXISTE */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("PARAMETRO_INVALIDO", "El valor de '" + e.getName() + "' no es válido"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorRespuesta> faltaArchivo(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorRespuesta("FALTA_ARCHIVO", "Falta el archivo '" + e.getRequestPartName() + "'"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorRespuesta> archivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorRespuesta("ARCHIVO_DEMASIADO_GRANDE", "La foto no puede pasar de 10 MB"));
    }

    /**
     * La base de datos rechazó la operación por una restricción (UNIQUE, CHECK...).
     * Suele pasar cuando dos peticiones llegan a la vez (p. ej. doble clic en "Votar").
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> restriccion(DataIntegrityViolationException e) {
        log.warn("Restricción de la BD violada: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorRespuesta("CONFLICTO", "La operación choca con datos existentes"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> noExiste(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorRespuesta("NO_ENCONTRADO", "La ruta no existe"));
    }

    /** Cualquier otro error: se registra entero en el log, pero al cliente no se le dan detalles. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorRespuesta("ERROR_INTERNO", "Ha ocurrido un error inesperado"));
    }
}
