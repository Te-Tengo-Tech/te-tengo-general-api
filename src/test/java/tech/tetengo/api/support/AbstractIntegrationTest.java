package tech.tetengo.api.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** Base de las pruebas de integración: contexto completo, PostgreSQL real y JWT de prueba. */
@Tag("integration")
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.public-key-location=")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JwtDePrueba.class})
public abstract class AbstractIntegrationTest {}
