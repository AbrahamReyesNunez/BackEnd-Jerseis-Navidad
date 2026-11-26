package com.jerseys.backend.admin;

import com.jerseys.backend.admin.AnalizadorSospechosos.DatosCuenta;
import com.jerseys.backend.admin.AnalizadorSospechosos.DatosVoto;
import com.jerseys.backend.admin.SospechososDtos.CuentaResumen;
import com.jerseys.backend.admin.SospechososDtos.GrupoIpSospechoso;
import com.jerseys.backend.admin.SospechososDtos.PanelSospechososResponse;
import com.jerseys.backend.admin.SospechososDtos.Senal;
import com.jerseys.backend.admin.SospechososDtos.VotoResumen;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Test UNITARIO con datos inventados: no hace falta base de datos. */
class AnalizadorSospechososTest {

    private static final Instant INICIO = Instant.parse("2026-12-10T09:00:00Z");

    @Test
    void variasCuentasRegistradasEnPocosMinutosEsRafaga() {
        List<DatosCuenta> cuentas = List.of(
                cuenta(1, "ip-casa", 0), cuenta(2, "ip-casa", 3), cuenta(3, "ip-casa", 6));

        PanelSospechososResponse panel = AnalizadorSospechosos.analizar(cuentas, List.of());

        assertThat(panel.grupos()).hasSize(1);
        GrupoIpSospechoso grupo = panel.grupos().getFirst();
        assertThat(grupo.ipHash()).isEqualTo("ip-casa");
        assertThat(grupo.numCuentas()).isEqualTo(3);
        assertThat(grupo.senales()).containsExactly(Senal.RAFAGA_REGISTROS);
        assertThat(grupo.explicaciones().getFirst()).contains("3 cuentas registradas");
    }

    @Test
    void unaClaseNormalCompartiendoIpNoEsSospechosa() {
        // 6 alumnos con la IP del instituto, registrados y votando en momentos distintos,
        // cada uno con su favorito: es lo normal y NO debe salir en el panel
        List<DatosCuenta> cuentas = new ArrayList<>();
        List<DatosVoto> votos = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            cuentas.add(cuenta(i, "ip-instituto", i * 60));
            votos.add(voto(i, 100 + i, 6, "ip-instituto", 1000 + i * 60));
        }

        PanelSospechososResponse panel = AnalizadorSospechosos.analizar(cuentas, votos);

        assertThat(panel.grupos()).isEmpty();
        assertThat(panel.papeletasIdenticas()).isEmpty();
    }

    @Test
    void muchasCuentasDeUnaIpDandoSeisPuntosAlMismoJersey() {
        // 4 cuentas de la misma IP, votando con una hora de diferencia (sin ráfaga);
        // 3 de las 4 (75 %) dan los 6 puntos al jersey 12
        List<DatosCuenta> cuentas = List.of(
                cuenta(1, "ip-x", 0), cuenta(2, "ip-x", 60), cuenta(3, "ip-x", 120), cuenta(4, "ip-x", 180));
        List<DatosVoto> votos = List.of(
                voto(1, 12, 6, "ip-x", 300), voto(2, 12, 6, "ip-x", 360),
                voto(3, 12, 6, "ip-x", 420), voto(4, 7, 6, "ip-x", 480));

        GrupoIpSospechoso grupo = AnalizadorSospechosos.analizar(cuentas, votos).grupos().getFirst();

        assertThat(grupo.senales()).containsExactly(Senal.MISMO_FAVORITO);
        assertThat(grupo.explicaciones().getFirst()).isEqualTo(
                "3 de las 4 cuentas que votaron desde esta IP dieron 6 puntos al jersey #12");
    }

    @Test
    void votosEnRafagaDesdeLaMismaIp() {
        List<DatosCuenta> cuentas = List.of(
                cuenta(1, "ip-a", 0), cuenta(2, "ip-b", 100), cuenta(3, "ip-c", 200));
        // Se registraron desde sitios distintos, pero votan juntos desde la misma IP
        List<DatosVoto> votos = List.of(
                voto(1, 5, 6, "ip-z", 500), voto(2, 6, 6, "ip-z", 502), voto(3, 7, 6, "ip-z", 504));

        GrupoIpSospechoso grupo = AnalizadorSospechosos.analizar(cuentas, votos).grupos().getFirst();

        assertThat(grupo.ipHash()).isEqualTo("ip-z");
        assertThat(grupo.senales()).containsExactly(Senal.RAFAGA_VOTOS);
        assertThat(grupo.cuentas()).extracting(CuentaResumen::id).containsExactly(1L, 2L, 3L);
        assertThat(grupo.cuentas().getFirst().fechaVoto()).isEqualTo(INICIO.plus(500, ChronoUnit.MINUTES));
    }

    @Test
    void detectaPapeletasIdenticasAunqueVenganDeIpsDistintas() {
        List<DatosCuenta> cuentas = List.of(
                cuenta(1, "ip-a", 0), cuenta(2, "ip-b", 100), cuenta(3, "ip-c", 200));
        List<DatosVoto> votos = new ArrayList<>();
        votos.addAll(papeleta(1, "ip-a", 300));
        votos.addAll(papeleta(2, "ip-b", 400));
        // La cuenta 3 vota distinto
        votos.add(voto(3, 9, 6, "ip-c", 500));
        votos.add(voto(3, 8, 5, "ip-c", 500));
        votos.add(voto(3, 7, 4, "ip-c", 500));

        PanelSospechososResponse panel = AnalizadorSospechosos.analizar(cuentas, votos);

        assertThat(panel.papeletasIdenticas()).hasSize(1);
        assertThat(panel.papeletasIdenticas().getFirst().cuentas()).extracting(CuentaResumen::id)
                .containsExactly(1L, 2L);
        assertThat(panel.papeletasIdenticas().getFirst().papeleta()).containsExactly(
                new VotoResumen(12L, 6), new VotoResumen(3L, 5), new VotoResumen(7L, 4));
    }

    @Test
    void papeletasDeMenosDeTresVotosNoCuentan() {
        // Con solo 2 jerseys en el concurso, coincidir es fácil por casualidad
        List<DatosCuenta> cuentas = List.of(cuenta(1, "ip-a", 0), cuenta(2, "ip-b", 100));
        List<DatosVoto> votos = List.of(
                voto(1, 12, 6, "ip-a", 300), voto(1, 3, 5, "ip-a", 300),
                voto(2, 12, 6, "ip-b", 400), voto(2, 3, 5, "ip-b", 400));

        assertThat(AnalizadorSospechosos.analizar(cuentas, votos).papeletasIdenticas()).isEmpty();
    }

    @Test
    void ventanaDeslizante() {
        assertThat(AnalizadorSospechosos.maximoEnVentana(List.of())).isZero();
        assertThat(AnalizadorSospechosos.maximoEnVentana(List.of(min(0), min(10), min(11), min(30))))
                .isEqualTo(2); // 0-10 caben juntos (justo 10 min); 10-11 también; el 30 va solo
        assertThat(AnalizadorSospechosos.maximoEnVentana(List.of(min(20), min(0), min(5), min(9), min(40))))
                .isEqualTo(3); // da igual el orden de entrada
    }

    // ===================== Ayudantes =====================

    private static Instant min(int minutos) {
        return INICIO.plus(minutos, ChronoUnit.MINUTES);
    }

    private static DatosCuenta cuenta(long id, String ip, int minutoRegistro) {
        return new DatosCuenta(id, "u" + id + "@test.com", "Usuario " + id, min(minutoRegistro), true, true, ip);
    }

    private static DatosVoto voto(long usuario, long jersey, int puntos, String ip, int minuto) {
        return new DatosVoto(usuario, jersey, puntos, min(minuto), ip);
    }

    /** La misma papeleta de 3 votos: jersey 12 → 6, jersey 3 → 5, jersey 7 → 4. */
    private static List<DatosVoto> papeleta(long usuario, String ip, int minuto) {
        return List.of(voto(usuario, 12, 6, ip, minuto), voto(usuario, 3, 5, ip, minuto),
                voto(usuario, 7, 4, ip, minuto));
    }
}
