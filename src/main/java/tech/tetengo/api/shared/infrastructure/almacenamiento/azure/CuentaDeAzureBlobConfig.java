package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import tech.tetengo.api.shared.infrastructure.almacenamiento.ProveedorDeAlmacenamiento;

/** The Azure Blob Storage account, only with {@code tetengo.almacenamiento.proveedor=azure}. */
@Configuration(proxyBeanMethods = false)
class CuentaDeAzureBlobConfig {

    @Bean
    @Conditional(ProveedorDeAlmacenamiento.ConAzure.class)
    CuentaDeAzureBlob cuentaDeAzureBlob(PropiedadesDeAzureBlob propiedades, Clock reloj) {
        return CuentaDeAzureBlob.crear(propiedades, reloj);
    }
}
