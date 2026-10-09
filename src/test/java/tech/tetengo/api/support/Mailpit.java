package tech.tetengo.api.support;

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Mailpit, a local SMTP server with an HTTP API to read what it got, for the SMTP adapter test. */
public final class Mailpit {

    public static final DockerImageName IMAGEN = DockerImageName.parse("axllent/mailpit:v1.31.2");

    public static final int PUERTO_SMTP = 1025;

    public static final int PUERTO_HTTP = 8025;

    private Mailpit() {}

    public static GenericContainer<?> contenedor() {
        return new GenericContainer<>(IMAGEN)
                .withExposedPorts(PUERTO_SMTP, PUERTO_HTTP)
                .waitingFor(Wait.forHttp("/livez").forPort(PUERTO_HTTP).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    /** Base of Mailpit's HTTP API, e.g. {@code GET /api/v1/messages}. */
    public static URI api(GenericContainer<?> mailpit) {
        return URI.create("http://%s:%d".formatted(mailpit.getHost(), mailpit.getMappedPort(PUERTO_HTTP)));
    }
}
