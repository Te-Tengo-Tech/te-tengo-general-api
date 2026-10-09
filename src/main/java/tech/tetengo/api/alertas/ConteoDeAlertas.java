package tech.tetengo.api.alertas;

import java.time.Instant;

/** Public API of {@code alertas} for the weekly summary of {@code historial} (US-27). */
public interface ConteoDeAlertas {

    /**
     * Alerts of the household in context that happened in {@code [desde, hasta)}, by type. False alarms
     * are counted apart and left out of the falls (CA-19.2) and the unstable movements.
     */
    Conteo contar(Instant desde, Instant hasta);

    record Conteo(long caidas, long movimientosInestables, long falsasAlarmas) {}
}
