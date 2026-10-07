package tech.tetengo.api.shared.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Push delivery port. {@code tetengo.push.proveedor} chooses the adapter: Firebase Cloud Messaging,
 * Amazon SNS (FCM on Android, APNs or FCM on iOS), the iOS simulator, or a logging one by default
 * (docs/NOTIFICATIONS.md); tests record the notices. Throws {@link FallaDePush} when the service
 * does not respond or accepts none of the devices, so the caller can retry (CA-16.4).
 */
public interface NotificadorPush {

    /** Sends the notice to every destination and reports what the service did with them. */
    Resultado enviar(List<Destino> destinos, Aviso aviso);

    enum Plataforma {
        ANDROID,
        IOS
    }

    /**
     * A device. {@code referencia} is the provider's own address of the device, stored with it (the
     * SNS platform endpoint ARN); null when the provider has none yet or needs none.
     */
    record Destino(String tokenPush, Plataforma plataforma, String referencia) {

        public Destino(String tokenPush, Plataforma plataforma) {
            this(tokenPush, plataforma, null);
        }
    }

    /**
     * A notice: the data payload of the API contract ({@code {tipo, alertaId?, camaraId?, habitacion?,
     * ocurridaEn}}) plus the {@link Detalle} its title and body need.
     */
    record Aviso(TipoAviso tipo, UUID alertaId, UUID camaraId, String habitacion, Instant ocurridaEn, Detalle detalle) {

        public Aviso {
            detalle = detalle == null ? Detalle.NINGUNO : detalle;
        }

        public Aviso(TipoAviso tipo, UUID alertaId, UUID camaraId, String habitacion, Instant ocurridaEn) {
            this(tipo, alertaId, camaraId, habitacion, ocurridaEn, Detalle.NINGUNO);
        }

        public Aviso conDetalle(Detalle nuevo) {
            return new Aviso(tipo, alertaId, camaraId, habitacion, ocurridaEn, nuevo);
        }
    }

    /**
     * What the prototype's notice text needs beyond the data payload; it is never sent as data. Every
     * value may be null when unknown or not needed by the notice type.
     *
     * @param adultoMayor first name of the older adult of the household («Rosa»)
     * @param tipoDeAlerta type of the notice's alert, for its label and for «Caída en la Sala»
     * @param desde when the alert started, for a fall that began as an unstable movement
     * @param esperaMinutos the household's wait before escalating (US-10)
     * @param quien first name of the member who attended the alert
     */
    record Detalle(String adultoMayor, TipoDeAlerta tipoDeAlerta, Instant desde, Integer esperaMinutos, String quien) {

        public static final Detalle NINGUNO = new Detalle(null, null, null, null, null);
    }

    enum TipoDeAlerta {
        CAIDA,
        MOVIMIENTO_INESTABLE
    }

    /**
     * What the service did with the destinations.
     *
     * @param aceptados devices the service accepted the notice for
     * @param tokensInvalidos push tokens the service reports as no longer valid (app uninstalled,
     *     token expired, endpoint disabled): their devices stop receiving notices
     * @param referencias new provider addresses by push token, to store with each device
     */
    record Resultado(int aceptados, Set<String> tokensInvalidos, Map<String, String> referencias) {

        public Resultado {
            tokensInvalidos = Set.copyOf(tokensInvalidos);
            referencias = Map.copyOf(referencias);
        }

        /** Every destination accepted, nothing to update. */
        public static Resultado aceptados(List<Destino> destinos) {
            return new Resultado(destinos.size(), Set.of(), Map.of());
        }
    }

    class FallaDePush extends RuntimeException {
        public FallaDePush(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
