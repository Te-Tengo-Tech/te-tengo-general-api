package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.BiFunction;

/**
 * User delegation SAS (Microsoft Entra ID, no account key): the URLs are signed with a user delegation
 * key the API asks the storage for with its managed identity. One key serves every URL until it would
 * expire before the URL does; it is then replaced by a new one valid for a day (7 days at most, the
 * service's limit).
 */
final class FirmaPorDelegacion implements FirmanteSas {

    static final Duration VIGENCIA_DE_LA_CLAVE = Duration.ofDays(1);
    static final Duration VIGENCIA_MAXIMA_DE_LA_CLAVE = Duration.ofDays(7);

    /** The key starts a little earlier than now, against clock skew between the VM and the storage. */
    static final Duration ADELANTO = Duration.ofMinutes(5);

    private final BiFunction<OffsetDateTime, OffsetDateTime, UserDelegationKey> pedirClave;
    private final Clock reloj;
    private UserDelegationKey clave;

    FirmaPorDelegacion(BiFunction<OffsetDateTime, OffsetDateTime, UserDelegationKey> pedirClave, Clock reloj) {
        this.pedirClave = pedirClave;
        this.reloj = reloj;
    }

    @Override
    public String firmar(BlobClient blob, BlobServiceSasSignatureValues valores) {
        return blob.generateUserDelegationSas(
                valores, claveHasta(valores.getExpiryTime().toInstant()));
    }

    /** A user delegation key still valid at {@code expiraEn}. */
    synchronized UserDelegationKey claveHasta(Instant expiraEn) {
        if (clave == null || clave.getSignedExpiry().toInstant().isBefore(expiraEn)) {
            Instant ahora = reloj.instant();
            Instant fin = ahora.plus(VIGENCIA_DE_LA_CLAVE);
            if (fin.isBefore(expiraEn)) {
                fin = expiraEn;
            }
            Instant limite = ahora.plus(VIGENCIA_MAXIMA_DE_LA_CLAVE);
            if (fin.isAfter(limite)) {
                fin = limite;
            }
            clave = pedirClave.apply(enUtc(ahora.minus(ADELANTO)), enUtc(fin));
        }
        return clave;
    }

    private static OffsetDateTime enUtc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
