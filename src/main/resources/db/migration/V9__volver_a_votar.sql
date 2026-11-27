-- =====================================================================
-- V9: si te anulan un voto, puedes volver a dar esos puntos a otro jersey.
--
-- Antes la BD impedía que un usuario repitiera puntuación (UNIQUE usuario + puntos),
-- contando también los votos anulados: con un voto de 3 anulado no se podía guardar
-- otro de 3. Ahora las reglas solo cuentan los votos que NO están anulados (índices
-- únicos "parciales", con WHERE). Los anulados se quedan en la tabla: el admin los
-- sigue viendo, por si hay que revisar algo.
-- =====================================================================

ALTER TABLE votos DROP CONSTRAINT un_voto_jersey;
ALTER TABLE votos DROP CONSTRAINT un_voto_puntos;

-- Un usuario no puede tener dos votos que cuenten al mismo jersey...
CREATE UNIQUE INDEX un_voto_jersey ON votos (usuario_id, jersey_id) WHERE anulado = FALSE;
-- ...ni dos votos que cuenten con la misma puntuación
CREATE UNIQUE INDEX un_voto_puntos ON votos (usuario_id, puntos) WHERE anulado = FALSE;
