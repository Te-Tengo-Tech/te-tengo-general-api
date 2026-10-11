package tech.tetengo.api.camaras.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc.Uso;

/** RFC 9700 §4.14.2: a refresh token works once; reusing it is detected. */
class TokenDeRenovacionDePcTest {

    private static final Instant T0 = Instant.parse("2026-10-10T15:00:00Z");

    private static TokenDeRenovacionDePc token() {
        return new TokenDeRenovacionDePc(
                UUID.randomUUID(), UUID.randomUUID(), "h", "te-tengo-captura", T0.plus(Duration.ofDays(90)));
    }

    @Test
    void sirveUnaVezYReusarloSeDetecta() {
        var token = token();
        assertThat(token.evaluar("te-tengo-captura", T0)).isEqualTo(Uso.VALIDO);
        token.usar(T0);
        assertThat(token.evaluar("te-tengo-captura", T0.plusSeconds(1))).isEqualTo(Uso.REUTILIZADO);
    }

    @Test
    void venceA90DiasYPerteneceASuCliente() {
        var token = token();
        assertThat(token.evaluar("otro", T0)).isEqualTo(Uso.OTRO_CLIENTE);
        assertThat(token.evaluar("te-tengo-captura", T0.plus(Duration.ofDays(90))))
                .isEqualTo(Uso.VENCIDO);
    }

    @Test
    void revocadoNoSirveAunqueNoSeHayaUsado() {
        var token = token();
        token.revocar(T0);
        token.usar(T0);
        assertThat(token.evaluar("te-tengo-captura", T0)).isEqualTo(Uso.REVOCADO);
    }
}
