-- =====================================================================
-- V8: "Borrar mi cuenta" y avisos por correo de las fases del concurso.
-- =====================================================================

-- 1. Cuándo borró el usuario su cuenta (NULL = no está borrada).
--    La fila NO se borra: se le quitan todos los datos personales (email, nombre,
--    contraseña, IP) y queda como "Cuenta borrada". Así no se rompen los votos,
--    jerseys y ganadores que apuntan a ella (claves ajenas).
ALTER TABLE usuarios ADD COLUMN borrada_en TIMESTAMPTZ;

-- 2. Cuándo se envió cada aviso a todos los participantes (NULL = todavía no).
--    Sirve para no mandar el mismo aviso dos veces.
ALTER TABLE configuracion ADD COLUMN aviso_votacion_enviado_en      TIMESTAMPTZ;
ALTER TABLE configuracion ADD COLUMN aviso_fin_votacion_enviado_en  TIMESTAMPTZ;
ALTER TABLE configuracion ADD COLUMN aviso_resultados_enviado_en    TIMESTAMPTZ;
