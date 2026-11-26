package com.jerseys.backend.voto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    /** ¿Ha votado ya este usuario en esta categoría? */
    boolean existsByUsuarioIdAndCategoria(Long usuarioId, Categoria categoria);

    /** Los votos de un usuario en una categoría, de más a menos puntos. */
    List<Voto> findByUsuarioIdAndCategoriaOrderByPuntosDesc(Long usuarioId, Categoria categoria);

    /** Votos válidos de una categoría, con su jersey y el autor del jersey cargados (para el ranking). */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.jersey j
            JOIN FETCH j.usuario
            WHERE v.categoria = :categoria
              AND v.anulado = false AND j.estado = com.jerseys.backend.jersey.EstadoJersey.APROBADO
            """)
    List<Voto> findValidosParaRanking(Categoria categoria);

    /** Para el panel del admin: votos (de todas las categorías) con su votante y su jersey. */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.usuario
            JOIN FETCH v.jersey
            ORDER BY v.fecha DESC
            """)
    List<Voto> findAllParaAdmin();
}
