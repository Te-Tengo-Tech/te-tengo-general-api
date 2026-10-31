package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.SasProtocol;
import java.net.URI;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * The Azure Blob Storage account (ADR 0013): one blob service client, shared by the containers of the
 * clips, the movement files and the photos, and the way their SAS URLs are signed (account key, or a
 * user delegation key obtained with the managed identity).
 */
public final class CuentaDeAzureBlob {

    private static final Logger log = LoggerFactory.getLogger(CuentaDeAzureBlob.class);

    private final BlobServiceClient servicio;
    private final FirmanteSas firmante;
    private final SasProtocol protocolo;
    private final boolean crearContenedores;
    private final Clock reloj;

    CuentaDeAzureBlob(
            BlobServiceClient servicio,
            FirmanteSas firmante,
            SasProtocol protocolo,
            boolean crearContenedores,
            Clock reloj) {
        this.servicio = servicio;
        this.firmante = firmante;
        this.protocolo = protocolo;
        this.crearContenedores = crearContenedores;
        this.reloj = reloj;
    }

    public static CuentaDeAzureBlob crear(PropiedadesDeAzureBlob propiedades, Clock reloj) {
        URI endpoint = propiedades.endpointEfectivo();
        var constructor = new BlobServiceClientBuilder()
                .endpoint(endpoint.toString())
                .httpClient(new JdkHttpClientBuilder().build());
        FirmanteSas firmante;
        BlobServiceClient servicio;
        if (propiedades.conClave()) {
            if (!StringUtils.hasText(propiedades.cuenta())) {
                throw new IllegalStateException(
                        "tetengo.almacenamiento.azure.clave necesita tetengo.almacenamiento.azure.cuenta");
            }
            servicio = constructor
                    .credential(new StorageSharedKeyCredential(
                            propiedades.cuenta().trim(), propiedades.clave().trim()))
                    .buildClient();
            firmante = FirmanteSas.conClaveDeCuenta();
        } else {
            servicio = constructor
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            BlobServiceClient cliente = servicio;
            firmante = new FirmaPorDelegacion(cliente::getUserDelegationKey, reloj);
        }
        // HTTPS only, except against a local emulator served over plain HTTP.
        SasProtocol protocolo =
                "https".equalsIgnoreCase(endpoint.getScheme()) ? SasProtocol.HTTPS_ONLY : SasProtocol.HTTPS_HTTP;
        log.info(
                "Almacenamiento en Azure Blob: {} ({})",
                endpoint,
                propiedades.conClave() ? "clave de cuenta" : "Microsoft Entra ID, SAS de delegación de usuario");
        return new CuentaDeAzureBlob(servicio, firmante, protocolo, propiedades.crearContenedores(), reloj);
    }

    /** The container {@code nombre}, created first when {@code crear-contenedores} is on. */
    public ContenedorDeBlobs contenedor(String nombre) {
        var cliente = servicio.getBlobContainerClient(nombre);
        var contenedor = new ContenedorDeBlobs(cliente, firmante, protocolo, reloj);
        if (crearContenedores) {
            contenedor.crearSiFalta();
        }
        return contenedor;
    }
}
