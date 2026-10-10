package tech.tetengo.api.alertas.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CA-16.4 retries. How often and for how long are not in the backlog: implementation choices in
 * {@code application.yml}.
 *
 * @param reintentoCada time between attempts of a notice that was not delivered
 * @param intentosMaximos attempts of a notice that is not urgent (its deadline is
 *     {@code reintentoCada × intentosMaximos})
 * @param ventanaUrgente how long an urgent alert notice ({@code TipoAviso.urgente()}) is retried,
 *     also while no member has an active device, so a phone that registers again still gets it
 */
@ConfigurationProperties("tetengo.push")
public record PropiedadesDePush(
        Duration reintentoCada,
        int intentosMaximos,
        @DefaultValue("30m") Duration ventanaUrgente) {}
