-- =====================================================================
-- V7: "¿Has olvidado tu contraseña?"
--
-- 1. Los enlaces para cambiar la contraseña se guardan en la misma tabla que los
--    de verificar el email, con otro tipo. OJO: de estos NO se guarda el token,
--    sino su HMAC (como con las IPs). Así, si alguien llegara a leer la BD, no
--    podría usarlos para cambiar contraseñas ajenas.
--
-- 2. version_sesion: número que va dentro de cada JWT. Al cambiar la contraseña
--    se suma 1 y los JWT con el número viejo dejan de valer: se cierran todas
--    las sesiones abiertas (por si alguien había entrado con la contraseña vieja).
-- =====================================================================

ALTER TABLE tokens_verificacion DROP CONSTRAINT tokens_verificacion_tipo_check;
ALTER TABLE tokens_verificacion ADD CONSTRAINT tokens_verificacion_tipo_check
    CHECK (tipo IN ('VERIFICACION_EMAIL', 'RECUPERAR_PASSWORD'));

ALTER TABLE usuarios ADD COLUMN version_sesion INTEGER NOT NULL DEFAULT 0;
