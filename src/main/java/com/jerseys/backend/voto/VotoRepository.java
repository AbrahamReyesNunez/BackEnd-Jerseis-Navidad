package com.jerseys.backend.voto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    /** ¿Ha votado ya este usuario? */
    boolean existsByUsuarioId(Long usuarioId);

    /** Los votos de un usuario, de más a menos puntos. */
    List<Voto> findByUsuarioIdOrderByPuntosDesc(Long usuarioId);

    /** Votos válidos, con su jersey y el autor del jersey cargados (para el ranking). */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.jersey j
            JOIN FETCH j.usuario
            WHERE v.anulado = false AND j.estado = com.jerseys.backend.jersey.EstadoJersey.APROBADO
            """)
    List<Voto> findValidosParaRanking();

    /** Para el panel del admin: todos los votos con su votante y su jersey. */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.usuario
            JOIN FETCH v.jersey
            ORDER BY v.fecha DESC
            """)
    List<Voto> findAllParaAdmin();
}
