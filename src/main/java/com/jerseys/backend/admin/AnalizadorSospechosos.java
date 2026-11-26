package com.jerseys.backend.admin;

import com.jerseys.backend.admin.SospechososDtos.CuentaResumen;
import com.jerseys.backend.admin.SospechososDtos.GrupoIpSospechoso;
import com.jerseys.backend.admin.SospechososDtos.PanelSospechososResponse;
import com.jerseys.backend.admin.SospechososDtos.PapeletasIdenticas;
import com.jerseys.backend.admin.SospechososDtos.Senal;
import com.jerseys.backend.admin.SospechososDtos.VotoResumen;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Busca SEÑALES de trampa en los registros y los votos. No acusa a nadie: el admin revisa
 * cada caso y decide (bloquear la cuenta, anular votos...).
 *
 * ⚠️ En el instituto todos los alumnos salen a Internet con la MISMA IP. Que muchas cuentas
 * compartan IP es normal; lo raro es que además coincidan en tiempo o en sus votos.
 *
 * Esta clase solo hace cálculos con listas: no toca la base de datos. Así se puede probar
 * con datos inventados (ver AnalizadorSospechososTest).
 */
public final class AnalizadorSospechosos {

    /** Ventana de tiempo para detectar ráfagas. */
    static final Duration VENTANA = Duration.ofMinutes(10);
    /** Cuántas cuentas dentro de la ventana cuentan como ráfaga. */
    static final int MIN_RAFAGA = 3;
    /** Mínimo de cuentas que dan la máxima puntuación al mismo jersey... */
    static final int MIN_MISMO_FAVORITO = 3;
    /** ...y qué porcentaje de los votantes de esa IP tienen que ser. */
    static final int PORCENTAJE_MISMO_FAVORITO = 60;
    /** Con pocas puntuaciones, que dos papeletas coincidan puede ser casualidad. */
    static final int MIN_VOTOS_PAPELETA = 3;

    private static final int PUNTOS_MAXIMOS = 6;

    /** Datos de una cuenta que necesita el análisis. */
    public record DatosCuenta(Long id, String email, String nombre, Instant fechaRegistro,
                              boolean verificado, boolean activo, String ipRegistroHash) {}

    /** Datos de un voto (no anulado) que necesita el análisis. */
    public record DatosVoto(Long usuarioId, Long jerseyId, int puntos, Instant fecha, String ipHash) {}

    private AnalizadorSospechosos() {
    }

    public static PanelSospechososResponse analizar(List<DatosCuenta> cuentas, List<DatosVoto> votos) {
        Map<Long, DatosCuenta> cuentaPorId = cuentas.stream()
                .collect(Collectors.toMap(DatosCuenta::id, c -> c));
        Map<Long, List<DatosVoto>> votosPorUsuario = votos.stream()
                .collect(Collectors.groupingBy(DatosVoto::usuarioId));

        return new PanelSospechososResponse(
                gruposPorIp(cuentas, votos, cuentaPorId, votosPorUsuario),
                papeletasIdenticas(cuentaPorId, votosPorUsuario));
    }

    // ===================== Grupos por IP =====================

    private static List<GrupoIpSospechoso> gruposPorIp(List<DatosCuenta> cuentas, List<DatosVoto> votos,
                                                       Map<Long, DatosCuenta> cuentaPorId,
                                                       Map<Long, List<DatosVoto>> votosPorUsuario) {
        // Para cada IP: quién se registró desde ella y quién votó desde ella (y cuándo)
        Map<String, List<DatosCuenta>> registrosPorIp = new HashMap<>();
        for (DatosCuenta c : cuentas) {
            if (c.ipRegistroHash() != null) {
                registrosPorIp.computeIfAbsent(c.ipRegistroHash(), ip -> new ArrayList<>()).add(c);
            }
        }
        Map<String, Map<Long, List<DatosVoto>>> votosPorIp = new HashMap<>();
        for (DatosVoto v : votos) {
            if (v.ipHash() != null) {
                votosPorIp.computeIfAbsent(v.ipHash(), ip -> new HashMap<>())
                        .computeIfAbsent(v.usuarioId(), id -> new ArrayList<>()).add(v);
            }
        }

        TreeSet<String> ips = new TreeSet<>(registrosPorIp.keySet());
        ips.addAll(votosPorIp.keySet());

        List<GrupoIpSospechoso> grupos = new ArrayList<>();
        for (String ip : ips) {
            List<DatosCuenta> registrados = registrosPorIp.getOrDefault(ip, List.of());
            Map<Long, List<DatosVoto>> votantes = votosPorIp.getOrDefault(ip, Map.of());

            TreeSet<Long> idsDelGrupo = new TreeSet<>(votantes.keySet());
            registrados.forEach(c -> idsDelGrupo.add(c.id()));
            if (idsDelGrupo.size() < 2) {
                continue; // una sola cuenta por IP: nada que comparar
            }

            List<Senal> senales = new ArrayList<>();
            List<String> explicaciones = new ArrayList<>();

            int rafagaRegistros = maximoEnVentana(registrados.stream().map(DatosCuenta::fechaRegistro).toList());
            if (rafagaRegistros >= MIN_RAFAGA) {
                senales.add(Senal.RAFAGA_REGISTROS);
                explicaciones.add(rafagaRegistros + " cuentas registradas desde esta IP en menos de "
                        + VENTANA.toMinutes() + " minutos");
            }

            // Cada usuario vota todo de golpe: nos quedamos con el momento de su primer voto
            List<Instant> momentosDeVoto = votantes.values().stream()
                    .map(vs -> vs.stream().map(DatosVoto::fecha).min(Comparator.naturalOrder()).orElseThrow())
                    .toList();
            int rafagaVotos = maximoEnVentana(momentosDeVoto);
            if (rafagaVotos >= MIN_RAFAGA) {
                senales.add(Senal.RAFAGA_VOTOS);
                explicaciones.add(rafagaVotos + " cuentas votaron desde esta IP en menos de "
                        + VENTANA.toMinutes() + " minutos");
            }

            mismoFavorito(votantes).ifPresent(texto -> {
                senales.add(Senal.MISMO_FAVORITO);
                explicaciones.add(texto);
            });

            if (!senales.isEmpty()) {
                List<CuentaResumen> resumen = idsDelGrupo.stream()
                        .map(cuentaPorId::get)
                        .filter(Objects::nonNull)
                        .map(c -> resumen(c, votosPorUsuario))
                        .toList();
                grupos.add(new GrupoIpSospechoso(ip, resumen.size(), senales, explicaciones, resumen));
            }
        }

        // Primero los que tienen más señales; a igualdad, los que tienen más cuentas
        grupos.sort(Comparator.comparingInt((GrupoIpSospechoso g) -> g.senales().size()).reversed()
                .thenComparing(Comparator.comparingInt(GrupoIpSospechoso::numCuentas).reversed()));
        return grupos;
    }

    /** ¿Muchas de las cuentas que votaron desde esta IP dieron los 6 puntos al mismo jersey? */
    private static Optional<String> mismoFavorito(Map<Long, List<DatosVoto>> votantes) {
        Map<Long, Long> seisPuntosPorJersey = votantes.values().stream()
                .flatMap(Collection::stream)
                .filter(v -> v.puntos() == PUNTOS_MAXIMOS)
                .collect(Collectors.groupingBy(DatosVoto::jerseyId, Collectors.counting()));

        return seisPuntosPorJersey.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() >= MIN_MISMO_FAVORITO
                        && e.getValue() * 100 >= (long) PORCENTAJE_MISMO_FAVORITO * votantes.size())
                .map(e -> e.getValue() + " de las " + votantes.size() + " cuentas que votaron desde esta IP "
                        + "dieron " + PUNTOS_MAXIMOS + " puntos al jersey #" + e.getKey());
    }

    /** El mayor número de instantes que caben a la vez dentro de VENTANA ("ventana deslizante"). */
    static int maximoEnVentana(List<Instant> instantes) {
        List<Instant> ordenados = instantes.stream().sorted().toList();
        int maximo = 0;
        int inicio = 0;
        for (int fin = 0; fin < ordenados.size(); fin++) {
            while (Duration.between(ordenados.get(inicio), ordenados.get(fin)).compareTo(VENTANA) > 0) {
                inicio++;
            }
            maximo = Math.max(maximo, fin - inicio + 1);
        }
        return maximo;
    }

    // ===================== Papeletas idénticas =====================

    private static List<PapeletasIdenticas> papeletasIdenticas(Map<Long, DatosCuenta> cuentaPorId,
                                                               Map<Long, List<DatosVoto>> votosPorUsuario) {
        // Clave = la papeleta escrita como texto, p. ej. "12:6,3:5,7:4" → cuentas que la han usado
        Map<String, List<Long>> cuentasPorPapeleta = new LinkedHashMap<>();
        votosPorUsuario.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_VOTOS_PAPELETA)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> cuentasPorPapeleta
                        .computeIfAbsent(clavePapeleta(e.getValue()), k -> new ArrayList<>())
                        .add(e.getKey()));

        return cuentasPorPapeleta.values().stream()
                .filter(ids -> ids.size() >= 2)
                .map(ids -> new PapeletasIdenticas(
                        papeleta(votosPorUsuario.get(ids.getFirst())),
                        ids.stream().map(cuentaPorId::get).filter(Objects::nonNull)
                                .map(c -> resumen(c, votosPorUsuario)).toList()))
                .sorted(Comparator.comparingInt((PapeletasIdenticas p) -> p.cuentas().size()).reversed())
                .toList();
    }

    private static String clavePapeleta(List<DatosVoto> votos) {
        return papeleta(votos).stream()
                .map(v -> v.jerseyId() + ":" + v.puntos())
                .collect(Collectors.joining(","));
    }

    private static List<VotoResumen> papeleta(List<DatosVoto> votos) {
        return votos.stream()
                .sorted(Comparator.comparingInt(DatosVoto::puntos).reversed())
                .map(v -> new VotoResumen(v.jerseyId(), v.puntos()))
                .toList();
    }

    private static CuentaResumen resumen(DatosCuenta c, Map<Long, List<DatosVoto>> votosPorUsuario) {
        Instant fechaVoto = votosPorUsuario.getOrDefault(c.id(), List.of()).stream()
                .map(DatosVoto::fecha).min(Comparator.naturalOrder()).orElse(null);
        return new CuentaResumen(c.id(), c.email(), c.nombre(), c.fechaRegistro(), c.verificado(), c.activo(),
                fechaVoto);
    }
}
