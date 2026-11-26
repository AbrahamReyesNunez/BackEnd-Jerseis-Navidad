-- =====================================================================
-- V3: cada usuario puede tener hasta 3 jerseys en el concurso (antes, 1).
--
-- Quitamos el índice único de V1, que solo dejaba UN jersey activo por usuario.
-- El máximo de 3 lo comprueba ahora JerseyService (una restricción de la BD no
-- puede contar filas). Para que dos subidas a la vez no se cuelen, JerseyService
-- bloquea la fila del usuario mientras sube (SELECT ... FOR UPDATE).
-- =====================================================================
DROP INDEX un_jersey_activo;

-- Índice normal (no único) para que contar los jerseys de un usuario sea rápido
CREATE INDEX idx_jerseys_usuario ON jerseys (usuario_id);
