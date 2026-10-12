package tech.tetengo.api.movimientos.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** Checks of the time ranges the PC and the app send ({@code 400 VALIDACION} with {@code campos}). */
public final class Rangos {

    /** The PC's clock may be a little ahead of the server's. */
    static final Duration TOLERANCIA = Duration.ofMinutes(1);

    private Rangos() {}

    /** {@code desde < hasta <= ahora + 1 min}, at most {@code maximo} long. */
    public static void exigir(Instant desde, Instant hasta, Duration maximo, Instant ahora) {
        if (desde == null) {
            throw invalido("desde", "Falta el inicio.");
        }
        if (hasta == null) {
            throw invalido("hasta", "Falta el fin.");
        }
        if (!hasta.isAfter(desde)) {
            throw invalido("hasta", "El fin debe ser posterior al inicio.");
        }
        if (Duration.between(desde, hasta).compareTo(maximo) > 0) {
            throw invalido("hasta", "El rango es demasiado largo (máximo %d min).".formatted(maximo.toMinutes()));
        }
        if (desde.isAfter(ahora.plus(TOLERANCIA))) {
            throw invalido("desde", "Elige un momento que ya pasó.");
        }
    }

    public static ErrorDeNegocio invalido(String campo, String mensaje) {
        return new ErrorDeNegocio(MovimientoError.VALIDACION, Map.of("campos", Map.of(campo, mensaje)));
    }
}
