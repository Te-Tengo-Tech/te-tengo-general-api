package tech.tetengo.api.shared.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables auditing ({@code creado_en}, {@code actualizado_en}) for {@code AuditableEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
