package tech.tetengo.api.shared.application.port;

import java.util.Collection;
import java.util.UUID;

/**
 * Implemented by {@code alertas}: how many active push devices some users have, so {@code hogares}
 * can tell the app whether anyone in the family can receive alerts (API contract §2,
 * {@code dispositivosActivos}).
 */
public interface DispositivosDePush {

    long activosDe(Collection<UUID> usuarioIds);
}
