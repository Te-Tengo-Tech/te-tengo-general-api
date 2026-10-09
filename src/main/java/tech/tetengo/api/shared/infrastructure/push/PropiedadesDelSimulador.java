package tech.tetengo.api.shared.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The iOS simulator, used when {@code tetengo.push.proveedor=simulador} (local only, macOS with
 * Xcode).
 *
 * @param bundleId bundle id of the app ({@code TT_SIMULADOR_BUNDLE_ID}); default: the one of
 *     te-tengo-mobile-flutter
 * @param dispositivo simulator that receives the pushes, as {@code xcrun simctl} names it
 */
@ConfigurationProperties("tetengo.push.simulador")
public record PropiedadesDelSimulador(
        @DefaultValue("tech.tetengo.teTengo") String bundleId,
        @DefaultValue("booted") String dispositivo) {}
