package tech.tetengo.api.hogares;

import java.time.Instant;
import java.util.UUID;

/**
 * API contract §13: the owner changed the older adult's photo («Carmen cambió la foto de Rosa», US-33).
 *
 * @param nombreAdultoMayor the older adult's first name, as the activity log shows it
 */
public record FotoDelAdultoMayorCambiada(UUID hogarId, UUID usuarioId, String nombreAdultoMayor, Instant ocurridaEn) {}
