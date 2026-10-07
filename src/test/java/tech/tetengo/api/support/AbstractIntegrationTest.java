package tech.tetengo.api.support;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base for integration tests: full context, real PostgreSQL, test JWTs and test doubles. Every test
 * starts from an empty database and a reset clock, so tests never depend on each other.
 */
@Tag("integration")
@SpringBootTest(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.public-key-location=",
            "tetengo.jwt.clave-privada-location="
        })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtDePrueba.class, SoporteDePruebas.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcDeLimpieza;

    @Autowired
    protected RelojDePrueba reloj;

    @BeforeEach
    void prepararEstadoInicial() {
        reloj.reiniciar();
        List<String> tablas = jdbcDeLimpieza.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
                String.class);
        if (!tablas.isEmpty()) {
            jdbcDeLimpieza.execute("truncate table " + String.join(", ", tablas) + " cascade");
        }
    }
}
