package tech.tetengo.api.monitoreo.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the denied access log (ADR 0015).
 *
 * @param nombreHabitacion the camera's room, null when it has none or is no longer in use
 * @param miembroDelHogar a member of this household asked, from this household
 * @param usuarioId who asked: only for a member; never revealed for anyone else
 * @param nombre the member's name, with {@code usuarioId}
 */
public record AccesoDenegadoRegistrado(
        Instant momento,
        String canal,
        String motivo,
        String accion,
        String protocolo,
        UUID camaraId,
        String nombreHabitacion,
        boolean miembroDelHogar,
        UUID usuarioId,
        String nombre) {}
