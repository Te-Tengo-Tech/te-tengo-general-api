package tech.tetengo.api.shared.infrastructure.web.limites;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory token buckets per key (a rule and a client address). Each {@link Limite} is a bucket of
 * {@code solicitudes} tokens that refills continuously over its {@code periodo}, so "10 per minute"
 * allows a burst of 10 and then one every 6 s. A request takes one token from every bucket of its
 * key, or none if any is empty: a rejected request does not count, so a client that waits the
 * {@code Retry-After} it was given always gets through.
 *
 * <p>Memory is bounded: keys whose buckets have all refilled are dropped once a minute, and beyond
 * {@code maxClaves} keys new ones are let through untracked (logged) rather than growing the map. The
 * state lives in one API instance and is lost on restart, which is enough for one public host.
 */
public class LimitadorDeSolicitudes {

    /** A bucket: {@code solicitudes} requests per {@code periodo}. */
    public record Limite(int solicitudes, Duration periodo) {
        public Limite {
            if (solicitudes < 1 || periodo == null || periodo.isNegative() || periodo.isZero()) {
                throw new IllegalArgumentException("Límite inválido: " + solicitudes + " por " + periodo);
            }
        }
    }

    /** Whether the request may go on and, if not, how long until one more token is available. */
    public record Resultado(boolean permitido, Duration reintentarEn) {
        static final Resultado PERMITIDO = new Resultado(true, Duration.ZERO);
    }

    private static final Logger log = LoggerFactory.getLogger(LimitadorDeSolicitudes.class);
    private static final long LIMPIEZA_CADA_MS = Duration.ofMinutes(1).toMillis();
    /** Rounding slack: a bucket refilled to 0.9999999 after the advised wait has its token. */
    private static final double HOLGURA = 1e-9;
    /** The same for the advised wait, so 660 s is not 660.000001 s rounded up to 661. */
    private static final double HOLGURA_MS = 1e-3;

    private final Clock reloj;
    private final int maxClaves;
    private final ConcurrentHashMap<String, Cubetas> cubetas = new ConcurrentHashMap<>();
    private final AtomicLong proximaLimpieza;

    public LimitadorDeSolicitudes(Clock reloj, int maxClaves) {
        this.reloj = reloj;
        this.maxClaves = maxClaves;
        this.proximaLimpieza = new AtomicLong(reloj.millis() + LIMPIEZA_CADA_MS);
    }

    /** Takes one request of {@code clave} from every limit, or tells how long to wait. */
    public Resultado consumir(String clave, List<Limite> limites) {
        if (limites.isEmpty()) {
            return Resultado.PERMITIDO;
        }
        long ahora = reloj.millis();
        limpiarSiToca(ahora);
        Cubetas propias = cubetas.get(clave);
        if (propias == null) {
            if (cubetas.size() >= maxClaves) {
                limpiar(ahora);
                if (cubetas.size() >= maxClaves) {
                    log.warn("Límite de solicitudes: {} direcciones a la vez; las nuevas no se cuentan", maxClaves);
                    return Resultado.PERMITIDO;
                }
            }
            propias = cubetas.computeIfAbsent(clave, c -> new Cubetas(limites, ahora));
        }
        return propias.consumir(ahora);
    }

    /** Forgets every key (tests). */
    public void reiniciar() {
        cubetas.clear();
    }

    int claves() {
        return cubetas.size();
    }

    private void limpiarSiToca(long ahora) {
        long toca = proximaLimpieza.get();
        if (ahora >= toca && proximaLimpieza.compareAndSet(toca, ahora + LIMPIEZA_CADA_MS)) {
            limpiar(ahora);
        }
    }

    private void limpiar(long ahora) {
        cubetas.values().removeIf(c -> c.llenas(ahora));
    }

    /** The buckets of one key; every access is synchronized on it. */
    private static final class Cubetas {

        private final List<Limite> limites;
        private final double[] fichas;
        private long ultima;

        Cubetas(List<Limite> limites, long ahora) {
            this.limites = List.copyOf(limites);
            this.fichas = new double[limites.size()];
            for (int i = 0; i < fichas.length; i++) {
                fichas[i] = limites.get(i).solicitudes();
            }
            this.ultima = ahora;
        }

        synchronized Resultado consumir(long ahora) {
            rellenar(ahora);
            long esperaMs = 0;
            for (int i = 0; i < fichas.length; i++) {
                if (fichas[i] < 1 - HOLGURA) {
                    esperaMs = Math.max(esperaMs, (long) Math.ceil((1 - fichas[i]) / porMs(i) - HOLGURA_MS));
                }
            }
            if (esperaMs > 0) {
                return new Resultado(false, Duration.ofMillis(esperaMs));
            }
            for (int i = 0; i < fichas.length; i++) {
                fichas[i] -= 1;
            }
            return Resultado.PERMITIDO;
        }

        /** Every bucket is full again: the key can be forgotten. */
        synchronized boolean llenas(long ahora) {
            rellenar(ahora);
            for (int i = 0; i < fichas.length; i++) {
                if (fichas[i] < limites.get(i).solicitudes()) {
                    return false;
                }
            }
            return true;
        }

        private void rellenar(long ahora) {
            // A clock that went back (tests) refills nothing.
            long transcurrido = Math.max(0, ahora - ultima);
            for (int i = 0; i < fichas.length; i++) {
                fichas[i] = Math.min(limites.get(i).solicitudes(), fichas[i] + transcurrido * porMs(i));
            }
            ultima = Math.max(ultima, ahora);
        }

        private double porMs(int i) {
            Limite limite = limites.get(i);
            return limite.solicitudes() / (double) limite.periodo().toMillis();
        }
    }
}
