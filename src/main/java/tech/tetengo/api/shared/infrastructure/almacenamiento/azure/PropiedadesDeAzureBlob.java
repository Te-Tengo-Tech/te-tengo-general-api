package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Azure Blob Storage account of the clips, movement files and photos (ADR 0013), used with {@code
 * tetengo.almacenamiento.proveedor=azure}. Each store names its own container ({@code
 * tetengo.clips.azure.contenedor}, {@code tetengo.movimientos.azure.contenedor}, {@code
 * tetengo.fotos.azure.contenedor}).
 *
 * @param endpoint blob service endpoint, {@code https://<cuenta>.blob.core.windows.net} (Azurite:
 *     {@code http://localhost:10000/devstoreaccount1}); blank derives it from {@code cuenta}. It is
 *     also the host of the SAS URLs, so the agent and the app must reach it
 * @param cuenta storage account name; required with {@code clave}
 * @param clave account key: service SAS signed with it (Azurite, tests). Blank (production) signs user
 *     delegation SAS with Microsoft Entra ID through the SDK's default credential chain (the VM's
 *     managed identity), which needs the "Storage Blob Data Contributor" role
 * @param crearContenedores create missing containers at startup (local runs and tests only)
 */
@ConfigurationProperties("tetengo.almacenamiento.azure")
public record PropiedadesDeAzureBlob(URI endpoint, String cuenta, String clave, boolean crearContenedores) {

    public PropiedadesDeAzureBlob {
        if (endpoint != null && endpoint.toString().isBlank()) {
            endpoint = null;
        }
    }

    boolean conClave() {
        return StringUtils.hasText(clave);
    }

    /** The configured endpoint, or the public one of {@code cuenta}. */
    URI endpointEfectivo() {
        if (endpoint != null) {
            return endpoint;
        }
        if (!StringUtils.hasText(cuenta)) {
            throw new IllegalStateException(
                    "tetengo.almacenamiento.proveedor=azure necesita tetengo.almacenamiento.azure.endpoint o .cuenta");
        }
        return URI.create("https://%s.blob.core.windows.net".formatted(cuenta.trim()));
    }
}
