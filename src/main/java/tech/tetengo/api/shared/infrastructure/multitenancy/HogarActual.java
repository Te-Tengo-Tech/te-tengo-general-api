package tech.tetengo.api.shared.infrastructure.multitenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Hogar (tenant) de la petición en curso. Lo fija {@link FiltroHogarActual} a partir del JWT y se limpia
 * siempre al terminar la petición, para que no se filtre a otra petición atendida por el mismo hilo.
 */
public final class HogarActual {

    private static final ThreadLocal<UUID> HOGAR = new ThreadLocal<>();

    private HogarActual() {}

    public static void fijar(UUID hogarId) {
        HOGAR.set(hogarId);
    }

    public static Optional<UUID> obtener() {
        return Optional.ofNullable(HOGAR.get());
    }

    public static void limpiar() {
        HOGAR.remove();
    }
}
