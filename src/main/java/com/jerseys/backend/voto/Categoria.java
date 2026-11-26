package com.jerseys.backend.voto;

/**
 * Categorías de premio, según las bases del concurso (apartado 5).
 * POPULAR la vota todo el mundo; las otras cuatro, solo el JURADO.
 * Todas usan el mismo sistema de votación 3-2-1.
 */
public enum Categoria {

    POPULAR("El favorito del público", "Para la fotografía que obtenga más votos populares"),
    DIVERTIDO("El más divertido", "Para el jersey que destaque por su sentido del humor"),
    ORIGINAL("El más original", "Para la propuesta más creativa e innovadora"),
    NAVIDENO("El más navideño", "Para el jersey que mejor represente el espíritu de la Navidad"),
    EXTRAVAGANTE("El más extravagante", "Para el diseño más llamativo y atrevido");

    private final String nombre;
    private final String descripcion;

    Categoria(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String nombre() { return nombre; }

    public String descripcion() { return descripcion; }

    /** true para las cuatro categorías que vota solo el jurado. */
    public boolean esDelJurado() {
        return this != POPULAR;
    }
}
