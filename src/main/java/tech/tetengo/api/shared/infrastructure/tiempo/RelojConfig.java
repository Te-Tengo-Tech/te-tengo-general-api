package tech.tetengo.api.shared.infrastructure.tiempo;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Every time-based rule (lockouts, link expiry, heartbeats, pauses, escalation) reads this clock. */
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
