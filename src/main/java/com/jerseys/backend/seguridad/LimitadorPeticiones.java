package com.jerseys.backend.seguridad;

import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.config.AppProperties;
import com.jerseys.backend.config.AppProperties.Limite;
import com.jerseys.backend.config.AppProperties.Limites;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita cuántas veces puede hacer cada IP una acción sensible (login, registro...).
 * Frena a quien prueba contraseñas a lo bruto o crea cuentas con un script.
 *
 * Funciona como un CUBO DE FICHAS (algoritmo "token bucket", librería Bucket4j):
 *   - Cada IP tiene un cubo por acción, que empieza lleno (p. ej. 30 fichas).
 *   - Cada petición gasta 1 ficha. Si el cubo está vacío → 429 Too Many Requests.
 *   - El cubo se va rellenando poco a poco (p. ej. 30 fichas por minuto, una cada 2 segundos).
 *
 * Los cubos se guardan en memoria: si se reinicia el backend, se vacían los contadores.
 * Uso, en un controlador:   limitador.comprobar(Accion.LOGIN, DatosCliente.de(peticion).ip());
 */
@Component
public class LimitadorPeticiones {

    public enum Accion { LOGIN, REGISTRO, REENVIAR_VERIFICACION }

    /** Tope de cubos en memoria. Si alguien llama desde miles de IPs distintas, no nos come la RAM. */
    private static final int MAX_CUBOS = 100_000;

    private record Clave(Accion accion, String ip) {}

    private final Limites limites;
    private final Map<Clave, Bucket> cubos = new ConcurrentHashMap<>();

    @Autowired // Spring usa este constructor; el otro es para los tests
    public LimitadorPeticiones(AppProperties props) {
        this(props.limites());
    }

    LimitadorPeticiones(Limites limites) {
        this.limites = limites;
    }

    /** Gasta una ficha del cubo de esa IP para esa acción, o lanza 429 si no quedan. */
    public void comprobar(Accion accion, String ip) {
        if (cubos.size() > MAX_CUBOS) {
            cubos.clear(); // caso extremo: empezamos de cero antes que quedarnos sin memoria
        }
        Bucket cubo = cubos.computeIfAbsent(new Clave(accion, ip), clave -> crearCubo(limiteDe(accion)));

        ConsumptionProbe resultado = cubo.tryConsumeAndReturnRemaining(1);
        if (!resultado.isConsumed()) {
            long segundos = Math.max(1, (resultado.getNanosToWaitForRefill() + 999_999_999) / 1_000_000_000);
            throw ApiException.demasiadasPeticiones("DEMASIADAS_PETICIONES",
                    "Demasiados intentos seguidos. Espera " + segundos + " segundos y vuelve a probar.");
        }
    }

    private Limite limiteDe(Accion accion) {
        return switch (accion) {
            case LOGIN -> limites.login();
            case REGISTRO -> limites.registro();
            case REENVIAR_VERIFICACION -> limites.reenviarVerificacion();
        };
    }

    private static Bucket crearCubo(Limite limite) {
        return Bucket.builder()
                // capacity: fichas del cubo lleno. refillGreedy: las repone poco a poco, no todas de golpe.
                .addLimit(l -> l.capacity(limite.capacidad()).refillGreedy(limite.capacidad(), limite.periodo()))
                .build();
    }
}
