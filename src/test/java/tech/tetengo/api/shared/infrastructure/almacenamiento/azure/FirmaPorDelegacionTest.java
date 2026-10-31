package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.blob.models.UserDelegationKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The user delegation key is reused while it outlives the URL, and renewed within the 7-day limit. */
class FirmaPorDelegacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-31T12:00:00Z");

    private final List<OffsetDateTime[]> pedidos = new ArrayList<>();

    private final FirmaPorDelegacion firma = new FirmaPorDelegacion(
            (inicio, fin) -> {
                pedidos.add(new OffsetDateTime[] {inicio, fin});
                return new UserDelegationKey().setSignedStart(inicio).setSignedExpiry(fin);
            },
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void pideUnaClaveDeUnDiaQueEmpiezaAntesPorElDesfaseDeRelojes() {
        UserDelegationKey clave = firma.claveHasta(AHORA.plus(Duration.ofMinutes(10)));

        assertThat(clave.getSignedStart().toInstant()).isEqualTo(AHORA.minus(Duration.ofMinutes(5)));
        assertThat(clave.getSignedExpiry().toInstant()).isEqualTo(AHORA.plus(Duration.ofDays(1)));
    }

    @Test
    void reutilizaLaClaveMientrasLaUrlVenceAntes() {
        var primera = firma.claveHasta(AHORA.plus(Duration.ofMinutes(10)));
        var segunda = firma.claveHasta(AHORA.plus(Duration.ofHours(1)));

        assertThat(segunda).isSameAs(primera);
        assertThat(pedidos).hasSize(1);
    }

    @Test
    void renuevaLaClaveCuandoLaUrlVenceDespuesYNoPasaDeSieteDias() {
        firma.claveHasta(AHORA.plus(Duration.ofMinutes(10)));

        var larga = firma.claveHasta(AHORA.plus(Duration.ofDays(3)));
        assertThat(larga.getSignedExpiry().toInstant()).isEqualTo(AHORA.plus(Duration.ofDays(3)));

        var maxima = firma.claveHasta(AHORA.plus(Duration.ofDays(30)));
        assertThat(maxima.getSignedExpiry().toInstant()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
        assertThat(pedidos).hasSize(3);
    }
}
