package tech.tetengo.api.shared.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Activa la auditoría ({@code creado_en}, {@code actualizado_en}) de {@code AuditableEntity}. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
