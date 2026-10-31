package tech.tetengo.api.support;

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.PropiedadesDeAzureBlob;

/** Azurite, Microsoft's local Azure Storage emulator (ADR 0013), for the Azure Blob adapter tests. */
public final class Azurite {

    /** Same image as te-tengo-infra's test harness. */
    public static final DockerImageName IMAGEN =
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.37.0");

    public static final int PUERTO = 10000;

    /** Azurite's well-known development account and key (public test values, not a secret). */
    public static final String CUENTA = "devstoreaccount1";

    public static final String CLAVE =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    private Azurite() {}

    public static GenericContainer<?> contenedor() {
        return new GenericContainer<>(IMAGEN)
                .withCommand(
                        "azurite-blob",
                        "--blobHost",
                        "0.0.0.0",
                        "--blobPort",
                        String.valueOf(PUERTO),
                        "--skipApiVersionCheck",
                        "--loose")
                .withExposedPorts(PUERTO)
                .waitingFor(Wait.forLogMessage(".*Azurite Blob service successfully listens on.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    public static URI endpoint(GenericContainer<?> azurite) {
        return URI.create("http://%s:%d/%s".formatted(azurite.getHost(), azurite.getMappedPort(PUERTO), CUENTA));
    }

    /** The account of the container, with its key, creating missing containers. */
    public static PropiedadesDeAzureBlob propiedades(GenericContainer<?> azurite) {
        return new PropiedadesDeAzureBlob(endpoint(azurite), CUENTA, CLAVE, true);
    }
}
