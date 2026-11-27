package com.jerseys.backend.premio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GanadorCategoriaRepository extends JpaRepository<GanadorCategoria, Categoria> {

    /** Todos los ganadores elegidos, con su jersey y el autor del jersey cargados. */
    @Query("SELECT g FROM GanadorCategoria g JOIN FETCH g.jersey j JOIN FETCH j.usuario")
    List<GanadorCategoria> findAllConJersey();

    /** ¿Ha ganado ya este jersey alguna categoría? */
    Optional<GanadorCategoria> findByJerseyId(Long jerseyId);
}
