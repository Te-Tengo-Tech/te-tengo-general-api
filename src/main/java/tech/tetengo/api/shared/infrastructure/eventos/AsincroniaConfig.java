package tech.tetengo.api.shared.infrastructure.eventos;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Cross-module listeners run after the publishing transaction commits, asynchronously
 * ({@code @Async @TransactionalEventListener}). Spring Modulith records each publication in
 * {@code event_publication}, so a failed listener can be retried. Listeners bind the household of
 * the event with {@code EjecutorEnHogar} before touching household data.
 */
@Configuration
@EnableAsync
public class AsincroniaConfig {}
