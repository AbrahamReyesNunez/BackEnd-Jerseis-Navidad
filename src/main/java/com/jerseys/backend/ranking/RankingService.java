package com.jerseys.backend.ranking;

import com.jerseys.backend.jersey.EstadoJersey;
import com.jerseys.backend.jersey.Jersey;
import com.jerseys.backend.jersey.JerseyRepository;
import com.jerseys.backend.jersey.JerseyService;
import com.jerseys.backend.voto.Voto;
import com.jerseys.backend.voto.VotoRepository;
import com.jerseys.backend.voto.VotoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calcula el ranking: suma de puntos de los votos válidos (no anulados).
 * Desempate: gana quien tenga más votos de 3; si siguen empatados, más de 2; luego más de 1.
 *
 * Se calcula en Java porque es fácil de leer y, para un concurso de este tamaño,
 * rapidísimo. Con millones de votos convendría hacerlo en SQL (GROUP BY).
 */
@Service
public class RankingService {

    public record PosicionRanking(int posicion, Long jerseyId, String imagenUrl, String autor, int puntos,
                                  int numeroVotos, Map<Integer, Integer> votosPorPuntuacion) {}

    private final VotoRepository votos;
    private final JerseyRepository jerseys;
    private final JerseyService jerseyService;

    public RankingService(VotoRepository votos, JerseyRepository jerseys, JerseyService jerseyService) {
        this.votos = votos;
        this.jerseys = jerseys;
        this.jerseyService = jerseyService;
    }

    @Transactional(readOnly = true)
    public List<PosicionRanking> calcular() {
        // Votos válidos agrupados por jersey
        Map<Long, List<Voto>> votosPorJersey = votos.findValidosParaRanking().stream()
                .collect(Collectors.groupingBy(v -> v.getJersey().getId()));

        // Partimos de TODOS los jerseys aprobados, para que salgan también los que tienen 0 votos
        List<Fila> filas = new ArrayList<>();
        for (Jersey j : jerseys.findByEstadoConUsuario(EstadoJersey.APROBADO)) {
            filas.add(new Fila(j, votosPorJersey.getOrDefault(j.getId(), List.of())));
        }

        Comparator<Fila> orden = Comparator.comparingInt(Fila::puntos).reversed();
        for (int p = VotoService.MAX_VOTOS; p >= 1; p--) {
            final int puntuacion = p;
            orden = orden.thenComparing(Comparator.comparingInt((Fila f) -> f.cuantosDe(puntuacion)).reversed());
        }
        orden = orden.thenComparing(f -> f.jersey().getId());
        filas.sort(orden);

        List<PosicionRanking> ranking = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            Fila f = filas.get(i);
            ranking.add(new PosicionRanking(i + 1, f.jersey().getId(), jerseyService.url(f.jersey()),
                    f.jersey().getUsuario().getNombre(), f.puntos(), f.votos().size(), f.desglose()));
        }
        return ranking;
    }

    private record Fila(Jersey jersey, List<Voto> votos) {
        int puntos() {
            return votos.stream().mapToInt(Voto::getPuntos).sum();
        }

        int cuantosDe(int puntuacion) {
            return (int) votos.stream().filter(v -> v.getPuntos() == puntuacion).count();
        }

        Map<Integer, Integer> desglose() {
            Map<Integer, Integer> mapa = new HashMap<>();
            for (int p = VotoService.MAX_VOTOS; p >= 1; p--) {
                mapa.put(p, cuantosDe(p));
            }
            return mapa;
        }
    }
}
