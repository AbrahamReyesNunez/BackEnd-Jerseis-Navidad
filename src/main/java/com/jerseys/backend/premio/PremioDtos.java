package com.jerseys.backend.premio;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class PremioDtos {

    private PremioDtos() {
    }

    // ----- Jurado -----

    /** Cuerpo de PUT /api/jurado/ganadores/{categoria}: { "jerseyId": 12 } */
    public record ElegirGanadorRequest(@NotNull(message = "Indica el jersey") Long jerseyId) {}

    /**
     * Un jersey en la pantalla del jurado. Es anónimo, como en la votación.
     * esPropio = true → es el tuyo: no puedes marcarlo tú.
     * ganadorDe = la categoría que ya ha ganado (null si ninguna): no puede ganar otra.
     */
    public record JerseyCandidatoResponse(Long id, String imagenUrl, String imagenTraseraUrl, boolean esPropio,
                                          Categoria ganadorDe) {}

    /** Una categoría del jurado y el jersey elegido (ganador = null si aún no han decidido). */
    public record CategoriaJuradoResponse(Categoria categoria, String nombre, String descripcion,
                                          JerseyCandidatoResponse ganador) {}

    // ----- Resultados -----

    /** El jersey ganador de un premio y su autor. */
    public record GanadorResponse(Long jerseyId, String imagenUrl, String imagenTraseraUrl, String autor) {}

    /** Un premio y su ganador (ganador = null si quedó desierto). */
    public record PremioResponse(Categoria categoria, String nombre, String descripcion, GanadorResponse ganador) {}

    /** Los 5 premios: primero el popular y luego las cuatro categorías del jurado. */
    public record ResultadosResponse(List<PremioResponse> premios) {}
}
