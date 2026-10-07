package tech.tetengo.api.support;

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Floci, the local AWS emulator of {@code compose.yaml} (ADR 0005), for adapter integration tests. */
public final class Floci {

    /** Same image as {@code compose.yaml}. */
    public static final DockerImageName IMAGEN = DockerImageName.parse("floci/floci:2.2.0");

    public static final int PUERTO = 4566;

    /** Floci's built-in access key and secret, the only ones it accepts when it verifies signatures. */
    public static final String CLAVE = "test";

    private Floci() {}

    public static GenericContainer<?> contenedor() {
        return new GenericContainer<>(IMAGEN)
                .withEnv("FLOCI_SERVICES_ECS_RECONCILE_CONTAINERS_ON_STARTUP", "false")
                .withExposedPorts(PUERTO)
                .waitingFor(Wait.forHttp("/_localstack/health").forPort(PUERTO).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    public static URI endpoint(GenericContainer<?> floci) {
        return URI.create("http://%s:%d".formatted(floci.getHost(), floci.getMappedPort(PUERTO)));
    }
}
