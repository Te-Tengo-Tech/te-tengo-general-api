package tech.tetengo.api.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Clock the tests can move (lockouts, link expiry, heartbeats, pauses, escalation). Starts at "now". */
public class RelojDePrueba extends Clock {

    private final AtomicReference<Instant> ahora = new AtomicReference<>();

    public RelojDePrueba() {
        reiniciar();
    }

    public void reiniciar() {
        ahora.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public void fijar(Instant instante) {
        ahora.set(instante);
    }

    public void avanzar(Duration duracion) {
        ahora.updateAndGet(i -> i.plus(duracion));
    }

    @Override
    public Instant instant() {
        return ahora.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(instant(), zona);
    }
}
