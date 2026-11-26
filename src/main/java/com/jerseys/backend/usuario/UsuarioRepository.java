package com.jerseys.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * REPOSITORIO: la capa que habla con la base de datos.
 *
 * Solo declaramos la interfaz: Spring Data genera la implementación sola.
 * - Hereda de JpaRepository: save(), findById(), findAll(), delete()... gratis.
 * - Los métodos "findBy..." se traducen a SQL a partir de su NOMBRE.
 *   findByEmailNormalizado(x) → SELECT * FROM usuarios WHERE email_normalizado = x
 * - Para consultas más complejas se usa @Query con JPQL (SQL sobre las entidades).
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailNormalizado(String emailNormalizado);

    boolean existsByEmailNormalizado(String emailNormalizado);

    boolean existsByRol(Rol rol);

    @Query("""
            SELECT u FROM Usuario u
            WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(u.nombre) LIKE LOWER(CONCAT('%', :texto, '%'))
            ORDER BY u.fechaRegistro DESC
            """)
    List<Usuario> buscar(String texto);

    List<Usuario> findAllByOrderByFechaRegistroDesc();
}
