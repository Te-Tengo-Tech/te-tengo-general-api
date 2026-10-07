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
 * Base for integration tests: full context, real PostgreSQL and test JWTs. Every test starts from an
 * empty database, so tests never depend on each other's data.
 */
@Tag("integration")
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.public-key-location=")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtDePrueba.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcDeLimpieza;

    @BeforeEach
    void vaciarBaseDeDatos() {
        List<String> tablas = jdbcDeLimpieza.queryForList(
                "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'",
                String.class);
        if (!tablas.isEmpty()) {
            jdbcDeLimpieza.execute("truncate table " + String.join(", ", tablas) + " cascade");
        }
    }
}
