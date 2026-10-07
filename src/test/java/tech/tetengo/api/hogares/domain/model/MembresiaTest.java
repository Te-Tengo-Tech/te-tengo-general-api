package tech.tetengo.api.hogares.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.shared.domain.model.Rol;

class MembresiaTest {

    @Test
    void soloAdmiteRolesDeHogar() {
        assertThat(new Membresia(UUID.randomUUID(), UUID.randomUUID(), Rol.TITULAR).esTitular())
                .isTrue();
        assertThat(new Membresia(UUID.randomUUID(), UUID.randomUUID(), Rol.INVITADO).esTitular())
                .isFalse();
        assertThatThrownBy(() -> new Membresia(UUID.randomUUID(), UUID.randomUUID(), Rol.AGENTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elAdultoMayorGuardaSusDatosLimpios() {
        AdultoMayor adulto = new AdultoMayor(" Rosa ", 78, " Lima ", Convivencia.CON_FAMILIAR, " 987 654 321 ");
        Hogar hogar = new Hogar(UUID.randomUUID(), adulto);
        assertThat(hogar.getAdultoMayor().getNombre()).isEqualTo("Rosa");
        assertThat(hogar.getAdultoMayor().getEdad()).isEqualTo(78);
        assertThat(hogar.getAdultoMayor().getDireccion()).isEqualTo("Lima");
        assertThat(hogar.getAdultoMayor().getTelefono()).isEqualTo("987 654 321");
    }

    @Test
    void unTelefonoEnBlancoNoSeGuarda() {
        assertThat(new AdultoMayor("Rosa", 78, "Lima", Convivencia.SOLO, "  ").getTelefono())
                .isNull();
        assertThat(new AdultoMayor("Rosa", 78, "Lima", Convivencia.SOLO, null).getTelefono())
                .isNull();
    }
}
