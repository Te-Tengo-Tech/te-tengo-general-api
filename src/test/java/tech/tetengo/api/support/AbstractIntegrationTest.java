package tech.tetengo.api.support;

import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base for integration tests: full context, real PostgreSQL, test JWTs and test doubles. Every test
 * starts from an empty database and a reset clock, so tests never depend on each other. Scheduled
 * jobs do not run on their own: tests call them.
 */
@Tag("integration")
@SpringBootTest(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location=",
            "tetengo.tareas.habilitadas=false"
        })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtDePrueba.class, SoporteDePruebas.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcDeLimpieza;

    @Autowired
    protected RelojDePrueba reloj;

    @Autowired
    protected CorreoDePrueba correos;

    @Autowired
    protected PushDePrueba push;

    @Autowired
    protected AlmacenamientoDePrueba almacenamiento;

    @BeforeEach
    void prepararEstadoInicial() {
        esperarEventosPendientes();
        reloj.reiniciar();
        correos.limpiar();
        push.limpiar();
        almacenamiento.limpiar();
        List<String> tablas = jdbcDeLimpieza.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
                String.class);
        if (!tablas.isEmpty()) {
            jdbcDeLimpieza.execute("truncate table " + String.join(", ", tablas) + " cascade");
        }
    }

    /**
     * Asynchronous listeners of the previous test may still be running; wait for them (up to a few
     * seconds) so they do not write into the next test's data.
     */
    private void esperarEventosPendientes() {
        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> jdbcDeLimpieza.queryForObject(
                                    "select count(*) from event_publication where status in ('PUBLISHED', 'PROCESSING', 'RESUBMITTED')",
                                    Integer.class)
                            == 0);
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            // A listener that keeps failing must not block the suite; the test that caused it fails on its own.
        }
    }
}
