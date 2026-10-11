package tech.tetengo.api.shared.infrastructure.web.limites;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tech.tetengo.api.support.AbstractIntegrationTest;

/**
 * A client that reaches the API directly, not through a trusted proxy, cannot pick its address with
 * {@code X-Forwarded-For}: here 127.0.0.1 is not a trusted proxy, so the header is ignored and every
 * request counts against 127.0.0.1.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false",
            AbstractIntegrationTest.SECRETO_MEDIAMTX,
            "tetengo.limites.habilitado=true",
            "tetengo.limites.vinculacion.por-minuto=2",
            // Only this address (TEST-NET-1) would be trusted.
            "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.1"
        })
class LimitePorIpSinProxyDeConfianzaIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int puerto;

    @Autowired
    LimitadorDeSolicitudes limitador;

    ClienteHttp api;

    @BeforeEach
    void limitesEnCero() {
        limitador.reiniciar();
        api = new ClienteHttp(puerto);
    }

    @Test
    void unXForwardedForQueNoVieneDeUnProxyDeConfianzaSeIgnora() throws Exception {
        assertThat(api.vinculacion("203.0.113.1").statusCode()).isEqualTo(200);
        assertThat(api.vinculacion("203.0.113.2").statusCode()).isEqualTo(200);

        assertThat(api.vinculacion("203.0.113.3").statusCode()).isEqualTo(429);
        assertThat(api.vinculacion(null).statusCode()).isEqualTo(429);
    }
}
