package com.jerseys.backend.premio;

/**
 * Los 5 premios del concurso, según las bases (apartado 5).
 * <ul>
 *   <li>POPULAR: lo decide el voto popular 3-2-1 (vota todo el mundo, también el jurado).</li>
 *   <li>Las otras cuatro: el jurado, reunido en persona, elige directamente el ganador de cada una.</li>
 * </ul>
 * Un jersey solo puede ganar UN premio: el ganador popular no puede ganar una categoría del jurado,
 * ni un mismo jersey dos categorías del jurado.
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

    /** true para las cuatro categorías que decide el jurado. */
    public boolean esDelJurado() {
        return this != POPULAR;
    }
}
