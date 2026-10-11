package tech.tetengo.api.shared.infrastructure.web.limites;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.tetengo.api.support.ApiDePrueba.campo;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tech.tetengo.api.support.AbstractIntegrationTest;

/**
 * The public endpoints are limited per client address, which comes from the reverse proxy's
 * {@code X-Forwarded-For} (the test connects from 127.0.0.1, a trusted internal proxy). Small limits
 * keep the tests short; the production values are in application.yml.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false",
            AbstractIntegrationTest.SECRETO_MEDIAMTX,
            "tetengo.limites.habilitado=true",
            "tetengo.limites.vinculacion.por-minuto=3",
            "tetengo.limites.vinculacion.por-hora=5",
            "tetengo.limites.token.por-minuto=2",
            "tetengo.limites.sesiones.por-minuto=2",
            "tetengo.limites.recuperaciones.por-minuto=1"
        })
class LimitePorIpIntegrationTest extends AbstractIntegrationTest {

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
    void pasadoElLimiteLaVinculacionResponde429ConRetryAfterYUnProblema() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(api.vinculacion("203.0.113.10").statusCode()).isEqualTo(200);
        }

        HttpResponse<String> rechazo = api.vinculacion("203.0.113.10");

        assertThat(rechazo.statusCode()).isEqualTo(429);
        // 3 per minute: one more every 20 s.
        assertThat(rechazo.headers().firstValue("Retry-After")).hasValue("20");
        assertThat(rechazo.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(rechazo.headers().firstValue("Content-Type"))
                .hasValueSatisfying(tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(campo(rechazo.body(), "$.status")).isEqualTo("429");
        assertThat(campo(rechazo.body(), "$.codigo")).isEqualTo(FiltroDeLimitePorIp.DEMASIADAS_SOLICITUDES);
        assertThat(campo(rechazo.body(), "$.instance")).isEqualTo(ClienteHttp.VINCULACION);
        assertThat(rechazo.body()).doesNotContain("\"error\"");

        reloj.avanzar(Duration.ofSeconds(20));
        assertThat(api.vinculacion("203.0.113.10").statusCode()).isEqualTo(200);
    }

    @Test
    void elLimitePorHoraTambienSeAplica() throws Exception {
        for (int i = 0; i < 3; i++) {
            api.vinculacion("203.0.113.10");
        }
        reloj.avanzar(Duration.ofMinutes(1));
        api.vinculacion("203.0.113.10");
        api.vinculacion("203.0.113.10");

        HttpResponse<String> rechazo = api.vinculacion("203.0.113.10");

        assertThat(rechazo.statusCode()).isEqualTo(429);
        // 5 per hour: one more every 12 minutes, minus the minute that passed.
        assertThat(rechazo.headers().firstValue("Retry-After")).hasValue("660");
    }

    @Test
    void cadaDireccionTieneSuPropioLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            api.vinculacion("203.0.113.10");
        }
        assertThat(api.vinculacion("203.0.113.10").statusCode()).isEqualTo(429);

        assertThat(api.vinculacion("203.0.113.11").statusCode()).isEqualTo(200);
        assertThat(api.vinculacion("2001:db8:1:2::1").statusCode()).isEqualTo(200);
    }

    @Test
    void unaDireccionQueElClientePoneAntesDeLaDelProxyNoCambiaSuLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            api.vinculacion("203.0.113.20");
        }

        // The proxy appends the address it saw; whatever the client sent before it is not trusted.
        assertThat(api.vinculacion("198.51.100.1, 203.0.113.20").statusCode()).isEqualTo(429);
        assertThat(api.vinculacion("198.51.100.2, 203.0.113.20").statusCode()).isEqualTo(429);
    }

    @Test
    void laConsultaDeLaPcPasadoElLimiteTambienPideIrMasLento() throws Exception {
        Map<String, String> consulta = Map.of(
                "grant_type",
                ClienteHttp.CONCESION_DISPOSITIVO,
                "device_code",
                "codigo-que-no-existe",
                "client_id",
                ClienteHttp.CLIENTE);
        for (int i = 0; i < 2; i++) {
            assertThat(api.formulario(ClienteHttp.TOKEN, "203.0.113.30", consulta)
                            .statusCode())
                    .isEqualTo(400);
        }

        HttpResponse<String> rechazo = api.formulario(ClienteHttp.TOKEN, "203.0.113.30", consulta);

        assertThat(rechazo.statusCode()).isEqualTo(429);
        assertThat(rechazo.headers().firstValue("Retry-After")).hasValue("30");
        assertThat(campo(rechazo.body(), "$.codigo")).isEqualTo(FiltroDeLimitePorIp.DEMASIADAS_SOLICITUDES);
        // RFC 8628 §3.5: an OAuth client that reads the body slows down.
        assertThat(campo(rechazo.body(), "$.error")).isEqualTo("slow_down");
    }

    @Test
    void elInicioDeSesionYLaRecuperacionTambienSeLimitanPorDireccion() throws Exception {
        String credenciales = "{\"correo\":\"nadie@correo.pe\",\"contrasena\":\"incorrecta\"}";
        for (int i = 0; i < 2; i++) {
            assertThat(api.json("/api/sesiones", "203.0.113.40", credenciales).statusCode())
                    .isEqualTo(401);
        }
        HttpResponse<String> sesion = api.json("/api/sesiones", "203.0.113.40", credenciales);
        assertThat(sesion.statusCode()).isEqualTo(429);
        assertThat(campo(sesion.body(), "$.codigo")).isEqualTo(FiltroDeLimitePorIp.DEMASIADAS_SOLICITUDES);

        String correo = "{\"correo\":\"nadie@correo.pe\"}";
        assertThat(api.json("/api/recuperaciones", "203.0.113.40", correo).statusCode())
                .isEqualTo(202);
        assertThat(api.json("/api/recuperaciones", "203.0.113.40", correo).statusCode())
                .isEqualTo(429);
    }

    @Test
    void lasDemasRutasNoSeLimitan() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(api.json("/api/sesiones/refresco", "203.0.113.50", "{\"tokenRefresco\":\"x\"}")
                            .statusCode())
                    .isEqualTo(401);
        }
    }
}
