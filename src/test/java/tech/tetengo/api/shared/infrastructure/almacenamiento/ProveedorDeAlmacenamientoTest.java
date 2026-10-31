package tech.tetengo.api.shared.infrastructure.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProveedorDeAlmacenamientoTest {

    @Test
    void sinValorEsS3() {
        assertThat(ProveedorDeAlmacenamiento.de(new MockEnvironment())).isEqualTo(ProveedorDeAlmacenamiento.S3);
        assertThat(ProveedorDeAlmacenamiento.de(
                        new MockEnvironment().withProperty(ProveedorDeAlmacenamiento.PROPIEDAD, " ")))
                .isEqualTo(ProveedorDeAlmacenamiento.S3);
    }

    @Test
    void azureSinImportarMayusculas() {
        assertThat(ProveedorDeAlmacenamiento.esAzure(
                        new MockEnvironment().withProperty(ProveedorDeAlmacenamiento.PROPIEDAD, "Azure")))
                .isTrue();
    }

    @Test
    void unValorDesconocidoDetieneLaAplicacion() {
        var entorno = new MockEnvironment().withProperty(ProveedorDeAlmacenamiento.PROPIEDAD, "r2");

        assertThatThrownBy(() -> ProveedorDeAlmacenamiento.de(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s3 o azure");
    }
}
