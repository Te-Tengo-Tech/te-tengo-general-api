package tech.tetengo.api.camaras.application;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The PC link (RFC 8628 device grant, ADR 0009). Values of the change request: a 10-minute user code
 * (CA-28.1), polling every 5 s (RFC 8628 §3.2), and 90 days without connecting before the PC must be
 * linked again (CA-28.7). The access token lifetime is an implementation choice.
 *
 * @param clientes {@code client_id}s of the public clients allowed to link (Te Tengo Captura)
 * @param uriVerificacion {@code verification_uri}: the app's page where the code is typed; the QR
 *     carries it with {@code ?codigo=XXXX-XXXX} ({@code verification_uri_complete})
 * @param vigenciaCodigo how long the user and device codes last
 * @param intervalo minimum polling interval
 * @param vigenciaTokenAcceso lifetime of the PC's access token and of its cameras' tokens
 * @param vigenciaTokenRenovacion lifetime of each refresh token: the PC must connect within it
 */
@ConfigurationProperties("tetengo.pcs")
public record PropiedadesDeVinculacion(
        Set<String> clientes,
        URI uriVerificacion,
        Duration vigenciaCodigo,
        Duration intervalo,
        Duration vigenciaTokenAcceso,
        Duration vigenciaTokenRenovacion) {

    public PropiedadesDeVinculacion {
        clientes = clientes == null ? Set.of() : Set.copyOf(clientes);
    }

    /** {@code verification_uri_complete} (RFC 8628 §3.3.1): the QR the PC shows. */
    public URI uriCompleta(String codigoLegible) {
        return UriComponentsBuilder.fromUri(uriVerificacion)
                .queryParam("codigo", codigoLegible)
                .build()
                .toUri();
    }
}
