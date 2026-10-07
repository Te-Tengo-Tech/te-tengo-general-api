package tech.tetengo.api.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** Base for integration tests: full context, real PostgreSQL and test JWTs. */
@Tag("integration")
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.public-key-location=")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtDePrueba.class})
public abstract class AbstractIntegrationTest {}
