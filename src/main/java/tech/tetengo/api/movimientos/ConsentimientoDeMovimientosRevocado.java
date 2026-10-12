package tech.tetengo.api.movimientos;

import java.time.Instant;
import java.util.UUID;

/**
 * US-35 / CA-35.4: the consent to store movements was revoked: nothing more is stored and the cloud
 * movements are deleted (also the clips' landmarks, CA-36.3).
 *
 * @param usuarioId the owner who revoked it; null when it ended with the monitoring consent
 * @param motivo {@code DESDE_LA_APP} or {@code CONSENTIMIENTO_DE_MONITOREO_REVOCADO}
 */
public record ConsentimientoDeMovimientosRevocado(UUID hogarId, UUID usuarioId, String motivo, Instant ocurridoEn) {}
