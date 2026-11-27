package com.jerseys.backend.voto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    /** Los votos de un usuario (también los anulados), de más a menos puntos. */
    List<Voto> findByUsuarioIdOrderByPuntosDesc(Long usuarioId);

    /** Votos válidos, con su jersey y el autor del jersey cargados (para el ranking). */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.jersey j
            JOIN FETCH j.usuario
            WHERE v.anulado = false AND j.estado = com.jerseys.backend.jersey.EstadoJersey.APROBADO
            """)
    List<Voto> findValidosParaRanking();

    /** Votos que siguen contando a un jersey, con su votante cargado (para avisarle si se anulan). */
    @Query("SELECT v FROM Voto v JOIN FETCH v.usuario WHERE v.jersey.id = :jerseyId AND v.anulado = false")
    List<Voto> findValidosAlJersey(Long jerseyId);

    /** Borra los votos que ha dado un usuario (al borrar su cuenta durante la votación). */
    @Modifying
    @Query("DELETE FROM Voto v WHERE v.usuario.id = :usuarioId")
    void borrarLosDe(Long usuarioId);

    /** Quita la IP y el navegador de los votos de un usuario (al borrar su cuenta después de la votación). */
    @Modifying
    @Query("UPDATE Voto v SET v.ipHash = null, v.userAgent = null WHERE v.usuario.id = :usuarioId")
    void anonimizarLosDe(Long usuarioId);

    /** Para el panel del admin: todos los votos con su votante y su jersey. */
    @Query("""
            SELECT v FROM Voto v
            JOIN FETCH v.usuario
            JOIN FETCH v.jersey
            ORDER BY v.fecha DESC
            """)
    List<Voto> findAllParaAdmin();
}
