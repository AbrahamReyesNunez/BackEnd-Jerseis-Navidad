-- =====================================================================
-- V4: rol JURADO y categorías de premio.
--
-- Según las bases del concurso hay 5 premios:
--   POPULAR       «El favorito del público» → vota todo el mundo (también el jurado)
--   DIVERTIDO     «El más divertido»        ┐
--   ORIGINAL      «El más original»         │ solo vota el JURADO,
--   NAVIDENO      «El más navideño»         │ con el mismo sistema 3-2-1
--   EXTRAVAGANTE  «El más extravagante»     ┘
--
-- Todos los votos van a la misma tabla, con una columna "categoria".
-- =====================================================================

-- 1. Nuevo rol JURADO
ALTER TABLE usuarios DROP CONSTRAINT usuarios_rol_check;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check
    CHECK (rol IN ('USUARIO', 'PROFESOR', 'JURADO', 'ADMIN'));

-- 2. Categoría de cada voto. Los votos que ya existían son del voto popular.
ALTER TABLE votos ADD COLUMN categoria VARCHAR(20) NOT NULL DEFAULT 'POPULAR'
    CONSTRAINT votos_categoria_check
    CHECK (categoria IN ('POPULAR', 'DIVERTIDO', 'ORIGINAL', 'NAVIDENO', 'EXTRAVAGANTE'));

-- 3. Las reglas "no votar dos veces el mismo jersey" y "no repetir puntuación"
--    ahora son DENTRO de cada categoría: un jurado puede dar 3 puntos al mismo
--    jersey en "Divertido" y en "Original".
ALTER TABLE votos DROP CONSTRAINT un_voto_jersey;
ALTER TABLE votos ADD CONSTRAINT un_voto_jersey UNIQUE (usuario_id, categoria, jersey_id);
ALTER TABLE votos DROP CONSTRAINT un_voto_puntos;
ALTER TABLE votos ADD CONSTRAINT un_voto_puntos UNIQUE (usuario_id, categoria, puntos);
