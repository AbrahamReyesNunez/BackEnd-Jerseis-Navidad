package com.jerseys.backend.admin;

import java.time.Instant;
import java.util.List;

/** Lo que devuelve GET /api/admin/sospechosos. Ver AnalizadorSospechosos. */
public final class SospechososDtos {

    private SospechososDtos() {
    }

    public enum Senal {
        /** Muchas cuentas registradas desde la misma IP en pocos minutos. */
        RAFAGA_REGISTROS,
        /** Muchas cuentas votando desde la misma IP en pocos minutos. */
        RAFAGA_VOTOS,
        /** La mayoría de las cuentas de una IP dieron la máxima puntuación al mismo jersey. */
        MISMO_FAVORITO
    }

    public record PanelSospechososResponse(
            /** IPs con varias cuentas y al menos una señal, de más a menos sospechosas. */
            List<GrupoIpSospechoso> grupos,
            /** Cuentas que han repartido exactamente los mismos puntos a los mismos jerseys. */
            List<PapeletasIdenticas> papeletasIdenticas
    ) {}

    public record GrupoIpSospechoso(
            /** Hash de la IP (la IP real no se guarda). Sirve para saber qué cuentas comparten IP. */
            String ipHash,
            int numCuentas,
            List<Senal> senales,
            /** Una frase por señal, lista para mostrar al admin. */
            List<String> explicaciones,
            List<CuentaResumen> cuentas
    ) {}

    public record PapeletasIdenticas(List<VotoResumen> papeleta, List<CuentaResumen> cuentas) {}

    public record VotoResumen(Long jerseyId, int puntos) {}

    /** fechaVoto es null si la cuenta todavía no ha votado. */
    public record CuentaResumen(Long id, String email, String nombre, Instant fechaRegistro,
                                boolean verificado, boolean activo, Instant fechaVoto) {}
}
