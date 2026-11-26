package com.jerseys.backend.jersey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JerseyRepository extends JpaRepository<Jersey, Long> {

    /** El jersey activo (no eliminado) de un usuario, si lo tiene. */
    Optional<Jersey> findFirstByUsuarioIdAndEstadoNot(Long usuarioId, EstadoJersey estado);

    /** El jersey más reciente del usuario, esté como esté (para "mi jersey"). */
    Optional<Jersey> findFirstByUsuarioIdOrderByFechaSubidaDesc(Long usuarioId);

    List<Jersey> findByEstadoOrderByFechaSubidaAsc(EstadoJersey estado);

    // "JOIN FETCH" carga también el usuario en la misma consulta (evita N consultas extra)
    @Query("SELECT j FROM Jersey j JOIN FETCH j.usuario ORDER BY j.fechaSubida DESC")
    List<Jersey> findAllConUsuario();

    @Query("SELECT j FROM Jersey j JOIN FETCH j.usuario WHERE j.estado = :estado ORDER BY j.fechaSubida DESC")
    List<Jersey> findByEstadoConUsuario(EstadoJersey estado);

    long countByEstadoAndUsuarioIdNot(EstadoJersey estado, Long usuarioId);
}
