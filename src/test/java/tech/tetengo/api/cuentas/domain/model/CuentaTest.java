package tech.tetengo.api.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CuentaTest {

    @Test
    void normalizaElCorreoYElNombre() {
        Cuenta cuenta = new Cuenta("  Ana.Perez@Correo.PE ", " Ana Pérez ", "hash");
        assertThat(cuenta.getCorreo()).isEqualTo("ana.perez@correo.pe");
        assertThat(cuenta.getNombre()).isEqualTo("Ana Pérez");
        assertThat(cuenta.getId()).isNotNull();
    }
}
