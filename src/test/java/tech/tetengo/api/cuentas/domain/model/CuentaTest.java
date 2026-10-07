package tech.tetengo.api.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

class CuentaTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    void normalizaElCorreoYElNombre() {
        Cuenta cuenta = new Cuenta("  Ana.Perez@Correo.PE ", " Ana Pérez ", "hash");
        assertThat(cuenta.getCorreo()).isEqualTo("ana.perez@correo.pe");
        assertThat(cuenta.getNombre()).isEqualTo("Ana Pérez");
        assertThat(cuenta.getId()).isNotNull();
    }

    @Test
    void ca02_2_unaContrasenaIncorrectaSeRechaza() {
        Cuenta cuenta = new Cuenta("ana@correo.pe", "Ana", "hash");
        assertThatThrownBy(() -> cuenta.autenticar(false, ahora))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(CuentaError.CREDENCIALES_INVALIDAS);
        assertThat(cuenta.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void ca02_3_elQuintoFalloSeguidoBloqueaQuinceMinutosEInformaHastaCuando() {
        Cuenta cuenta = new Cuenta("ana@correo.pe", "Ana", "hash");
        for (int i = 0; i < 4; i++) {
            fallar(cuenta, CuentaError.CREDENCIALES_INVALIDAS);
        }
        ErrorDeNegocio error = fallar(cuenta, CuentaError.CUENTA_BLOQUEADA);
        assertThat(error.propiedades()).containsEntry("bloqueadaHasta", ahora.plus(Duration.ofMinutes(15)));

        // While locked, even the right password is rejected.
        assertThatThrownBy(() -> cuenta.autenticar(true, ahora.plus(Duration.ofMinutes(14))))
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(CuentaError.CUENTA_BLOQUEADA);

        cuenta.autenticar(true, ahora.plus(Duration.ofMinutes(15)));
        assertThat(cuenta.estaBloqueada(ahora.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void unAccesoCorrectoReiniciaLosFallosConsecutivos() {
        Cuenta cuenta = new Cuenta("ana@correo.pe", "Ana", "hash");
        for (int i = 0; i < 4; i++) {
            fallar(cuenta, CuentaError.CREDENCIALES_INVALIDAS);
        }
        cuenta.autenticar(true, ahora);
        for (int i = 0; i < 4; i++) {
            fallar(cuenta, CuentaError.CREDENCIALES_INVALIDAS);
        }
        assertThat(cuenta.estaBloqueada(ahora)).isFalse();
    }

    private ErrorDeNegocio fallar(Cuenta cuenta, CuentaError esperado) {
        try {
            cuenta.autenticar(false, ahora);
        } catch (ErrorDeNegocio e) {
            assertThat(e.error()).isEqualTo(esperado);
            return e;
        }
        throw new AssertionError("Se esperaba un error");
    }
}
