package com.jerseys.backend.voto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    List<Voto> findByUsuarioIdOrderByPuntosDesc(Long usuarioId);

    /** Todos los votos válidos, con su jersey y el autor del jersey cargados (para el ranking). */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.jersey j
            JOIN FETCH j.usuario
            WHERE v.anulado = false AND j.estado = com.jerseys.backend.jersey.EstadoJersey.APROBADO
            """)
    List<Voto> findValidosParaRanking();

    /** Para el panel del admin: votos con su votante y su jersey. */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.usuario
            JOIN FETCH v.jersey
            ORDER BY v.fecha DESC
            """)
    List<Voto> findAllParaAdmin();
}
