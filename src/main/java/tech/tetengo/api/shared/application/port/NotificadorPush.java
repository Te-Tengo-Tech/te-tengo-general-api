package tech.tetengo.api.shared.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Push delivery port: Amazon SNS (FCM on Android, APNs on iOS) in production, see
 * {@code docs/BLOCKERS.md}; a logging adapter until then, and a recording one in tests. Throws
 * {@link FallaDePush} when the service does not respond, so the caller can retry (CA-16.4).
 */
public interface NotificadorPush {

    void enviar(List<Destino> destinos, Aviso aviso);

    enum Plataforma {
        ANDROID,
        IOS
    }

    record Destino(String tokenPush, Plataforma plataforma) {}

    /** Data payload of the API contract: {@code {tipo, alertaId?, camaraId?, habitacion?, ocurridaEn}}. */
    record Aviso(TipoAviso tipo, UUID alertaId, UUID camaraId, String habitacion, Instant ocurridaEn) {}

    class FallaDePush extends RuntimeException {
        public FallaDePush(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
