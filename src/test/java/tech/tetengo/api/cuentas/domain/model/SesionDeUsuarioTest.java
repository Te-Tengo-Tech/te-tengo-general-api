package tech.tetengo.api.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.domain.model.Rol;

class SesionDeUsuarioTest {

    private final Instant ahora = Instant.parse("2026-10-07T15:00:00Z");

    @Test
    void ca02_4_unaSesionCerradaDejaDeEstarVigente() {
        SesionDeUsuario sesion = new SesionDeUsuario(UUID.randomUUID(), null, null, "h", ahora.plusSeconds(60));
        assertThat(sesion.vigente(ahora)).isTrue();
        sesion.cerrar(ahora);
        assertThat(sesion.vigente(ahora)).isFalse();
    }

    @Test
    void venceAlLlegarASuExpiracion() {
        SesionDeUsuario sesion = new SesionDeUsuario(UUID.randomUUID(), null, null, "h", ahora);
        assertThat(sesion.vigente(ahora)).isFalse();
    }

    @Test
    void elHogarYElRolVanJuntos() {
        assertThatThrownBy(() -> new SesionDeUsuario(UUID.randomUUID(), UUID.randomUUID(), null, "h", ahora))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SesionDeUsuario(UUID.randomUUID(), UUID.randomUUID(), Rol.TITULAR, "h", ahora).getRol())
                .isEqualTo(Rol.TITULAR);
    }
}
