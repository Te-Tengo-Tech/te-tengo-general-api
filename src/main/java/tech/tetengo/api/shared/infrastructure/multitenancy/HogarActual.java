package tech.tetengo.api.shared.infrastructure.multitenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Household (tenant) of the current request. {@link FiltroHogarActual} sets it from the JWT and it is
 * always cleared when the request ends, so it never leaks into another request on the same thread.
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
