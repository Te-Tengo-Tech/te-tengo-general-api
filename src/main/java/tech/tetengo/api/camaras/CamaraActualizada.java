package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/**
 * US-29: a webcam of a linked PC changed how it is used: assigned to a room or moved (from the app or
 * the PC), its room renamed, or «Dejar de usar». The PC hears it on its control channel and starts or
 * stops that camera; only cameras in use capture.
 *
 * @param enUso false after «Dejar de usar»; then {@code ambienteId} and {@code nombreHabitacion} are null
 */
public record CamaraActualizada(
        UUID hogarId,
        UUID camaraId,
        UUID pcId,
        String dispositivoId,
        String nombreDispositivo,
        boolean enUso,
        UUID ambienteId,
        String nombreHabitacion,
        Instant ocurridaEn) {}
