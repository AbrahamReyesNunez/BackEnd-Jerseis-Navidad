package com.jerseys.backend.fase;

/**
 * Fases del concurso, en orden:
 *   PREPARACION → SUBIDA → ESPERA → VOTACION → CERRADO
 */
public enum Fase {
    /** Antes de que empiece la subida (o el admin aún no ha puesto las fechas). */
    PREPARACION,
    /** Se pueden subir jerseys. */
    SUBIDA,
    /** Entre el fin de la subida y el inicio de la votación. El admin revisa fotos. */
    ESPERA,
    /** Se puede votar. Ya no se pueden subir ni eliminar fotos. */
    VOTACION,
    /** Concurso terminado. */
    CERRADO
}
