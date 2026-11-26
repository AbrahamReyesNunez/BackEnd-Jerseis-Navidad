package com.jerseys.backend.jersey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JerseyRepository extends JpaRepository<Jersey, Long> {

    /** Cuántos jerseys tiene un usuario que NO estén en ese estado (p. ej. no eliminados). */
    long countByUsuarioIdAndEstadoNot(Long usuarioId, EstadoJersey estado);

    /** Todos los jerseys del usuario, el más reciente primero (para "mis jerseys"). */
    List<Jersey> findByUsuarioIdOrderByFechaSubidaDesc(Long usuarioId);

    List<Jersey> findByEstadoOrderByFechaSubidaAsc(EstadoJersey estado);

    // "JOIN FETCH" carga también el usuario en la misma consulta (evita N consultas extra)
    @Query("SELECT j FROM Jersey j JOIN FETCH j.usuario ORDER BY j.fechaSubida DESC")
    List<Jersey> findAllConUsuario();

    @Query("SELECT j FROM Jersey j JOIN FETCH j.usuario WHERE j.estado = :estado ORDER BY j.fechaSubida DESC")
    List<Jersey> findByEstadoConUsuario(EstadoJersey estado);

    long countByEstadoAndUsuarioIdNot(EstadoJersey estado, Long usuarioId);
}
