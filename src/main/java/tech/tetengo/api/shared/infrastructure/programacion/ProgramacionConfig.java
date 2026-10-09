package tech.tetengo.api.shared.infrastructure.programacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs (disconnection, pause end, escalation, deletion, retention). Every job is idempotent.
 * Tests turn scheduling off ({@code tetengo.tareas.habilitadas=false}) and run jobs explicitly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "tetengo.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class ProgramacionConfig {}
