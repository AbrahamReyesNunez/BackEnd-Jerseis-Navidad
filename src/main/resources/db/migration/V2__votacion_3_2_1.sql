-- =====================================================================
-- V2: sistema de votación definitivo → cada usuario reparte 3, 2 y 1 puntos
-- (antes era 6, 5, 4, 3, 2, 1).
--
-- La restricción CHECK de V1 no tenía nombre, así que PostgreSQL le puso
-- el nombre automático "votos_puntos_check". La cambiamos por la nueva.
-- (V1 NO se modifica: Flyway ya lo ha ejecutado en las bases de datos de todos.)
-- =====================================================================
ALTER TABLE votos DROP CONSTRAINT votos_puntos_check;
ALTER TABLE votos ADD CONSTRAINT votos_puntos_check CHECK (puntos BETWEEN 1 AND 3);
