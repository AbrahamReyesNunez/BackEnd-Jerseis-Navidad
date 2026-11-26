-- =====================================================================
-- V5: dos cambios del concurso.
-- =====================================================================

-- 1. Cada jersey puede tener una segunda foto, de la parte de detrás (opcional).
--    La de delante sigue siendo "imagen_nombre" (obligatoria).
ALTER TABLE jerseys ADD COLUMN imagen_trasera_nombre VARCHAR(100) UNIQUE;

-- 2. Solo pueden participar mayores de 18 (antes la casilla era "mayor de 14").
--    Es una confirmación DISTINTA, así que va en una columna nueva: las cuentas
--    que ya existían confirmaron 14 años, no 18, y se quedan en FALSE.
ALTER TABLE usuarios ADD COLUMN mayor_18_confirmado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios DROP COLUMN mayor_14_confirmado;
