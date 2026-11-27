-- =====================================================================
-- V6: el jurado ELIGE un ganador por categoría (ya no vota 3-2-1),
--     fechas de las fases del jurado y de los resultados, y fuera el rol PROFESOR.
--
-- Calendario del concurso (decidido por la clase):
--   SUBIDA            6 dic 12:00 → 10 dic 12:00
--   VOTACION         10 dic 12:00 → 13 dic 12:00   (voto popular 3-2-1, también el jurado)
--   DECISION_JURADO  13 dic 12:00 → 14 dic 12:00   (el jurado, reunido en persona, marca
--                                                    el ganador de cada categoría)
--   ESPERA_RESULTADOS 14 dic 12:00 → 16 dic 12:00
--   RESULTADOS       desde el 16 dic 12:00          (se publican los ganadores)
-- =====================================================================

-- 1. Fuera el rol PROFESOR: quien lo tuviera pasa a ser USUARIO normal.
--    Ahora solo el ADMIN ve los votos y el ranking.
UPDATE usuarios SET rol = 'USUARIO' WHERE rol = 'PROFESOR';
ALTER TABLE usuarios DROP CONSTRAINT usuarios_rol_check;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('USUARIO', 'JURADO', 'ADMIN'));

-- 2. Los votos vuelven a ser solo del voto popular: se borran los votos 3-2-1
--    del jurado (de pruebas) y la columna "categoria" deja de hacer falta.
DELETE FROM votos WHERE categoria <> 'POPULAR';
ALTER TABLE votos DROP CONSTRAINT un_voto_jersey;
ALTER TABLE votos DROP CONSTRAINT un_voto_puntos;
ALTER TABLE votos DROP COLUMN categoria;
ALTER TABLE votos ADD CONSTRAINT un_voto_jersey UNIQUE (usuario_id, jersey_id);
ALTER TABLE votos ADD CONSTRAINT un_voto_puntos UNIQUE (usuario_id, puntos);

-- 3. Ganador de cada categoría del jurado.
--    PRIMARY KEY (categoria) → como mucho un ganador por categoría.
--    UNIQUE (jersey_id)      → un jersey no puede ganar dos categorías.
CREATE TABLE ganadores_categoria (
    categoria    VARCHAR(20)  PRIMARY KEY
                 CHECK (categoria IN ('DIVERTIDO', 'ORIGINAL', 'NAVIDENO', 'EXTRAVAGANTE')),
    jersey_id    BIGINT       NOT NULL UNIQUE REFERENCES jerseys (id),
    -- Qué miembro del jurado lo marcó, y cuándo
    elegido_por  BIGINT       NOT NULL REFERENCES usuarios (id),
    fecha        TIMESTAMPTZ  NOT NULL
);

-- 4. Nuevas fechas. La decisión del jurado empieza justo cuando acaba la votación (fin_votacion).
ALTER TABLE configuracion ADD COLUMN fin_jurado TIMESTAMPTZ;
ALTER TABLE configuracion ADD COLUMN publicacion_resultados TIMESTAMPTZ;
