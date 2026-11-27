package com.jerseys.backend.fase;

/**
 * Fases del concurso, en orden:
 *   PREPARACION → SUBIDA → ESPERA → VOTACION → DECISION_JURADO → ESPERA_RESULTADOS → RESULTADOS
 */
public enum Fase {
    /** Antes de que empiece la subida (o el admin aún no ha puesto las fechas). */
    PREPARACION,
    /** Se pueden subir jerseys. */
    SUBIDA,
    /** Entre el fin de la subida y el inicio de la votación (si se deja hueco). El admin revisa fotos. */
    ESPERA,
    /** Voto popular. Ya no se pueden subir ni eliminar fotos. */
    VOTACION,
    /** El jurado, reunido en persona, marca el ganador de cada una de sus 4 categorías. */
    DECISION_JURADO,
    /** Ya está todo decidido, pero los resultados aún no se han publicado. */
    ESPERA_RESULTADOS,
    /** Resultados publicados (GET /api/resultados). Concurso terminado. */
    RESULTADOS
}
