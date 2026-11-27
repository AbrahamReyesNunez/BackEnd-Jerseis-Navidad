package com.jerseys.backend.fase;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Integer> {

    /** La fila bloqueada hasta que acabe la transacción (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Configuracion c WHERE c.id = :id")
    Optional<Configuracion> findBloqueando(Integer id);
}
