package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.AutorizarMediaMtx;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * MediaMTX's HTTP authorization hook (ADR 0007). Only MediaMTX calls it, from the server's internal
 * network, with the shared secret as the password of HTTP Basic credentials: its {@code authHTTPAddress}
 * is {@code http://mediamtx:<secret>@api:8080/...}, and Go's HTTP client sends the URL's user info as an
 * {@code Authorization: Basic} header and writes the password as {@code ***} in its errors, which
 * MediaMTX logs. The {@code secreto} query parameter of earlier deployments is still accepted, so the API
 * and MediaMTX can be updated in either order. The reverse proxy never routes {@code /api/interno/**}, and
 * a request from outside the private networks (one forwarded by the proxy has the client's address) is
 * denied whatever it carries.
 */
@RestController
@Tag(name = "Interno", description = "Called by the server's own services, never by the clients.")
class MediaMtxController {

    private final AutorizarMediaMtx autorizar;

    MediaMtxController(AutorizarMediaMtx autorizar) {
        this.autorizar = autorizar;
    }

    @Operation(
            summary = "Authorize a MediaMTX publish or read",
            description =
                    "MediaMTX authMethod http, from the server's internal network, with the shared secret as the password of HTTP Basic credentials (or the legacy secreto query parameter). 200 allows: publish on camaras/<camaraId> as user agente with the transmission's publish token (a webcam not in use only while its preview is open); read over HLS or WebRTC (WHEP), or playback, with ?token=<viewer token> of an open session of that camera (its preview for a webcam not in use). 401 for anything else, without the right secret, or from outside the private networks.")
    @SecurityRequirements
    @PostMapping(path = ApiVersioning.BASE + "/interno/mediamtx/autorizar", version = ApiVersioning.V1)
    ResponseEntity<Void> autorizar(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String credenciales,
            @RequestParam(required = false) String secreto,
            @RequestBody(required = false) AutorizacionMediaMtxRequest pedido,
            HttpServletRequest peticion) {
        boolean permitido = pedido != null
                && RedInterna.contiene(peticion.getRemoteAddr())
                && autorizar.ejecutar(
                        claveBasica(credenciales).orElse(secreto),
                        new PeticionDeMediaMtx(
                                pedido.action(),
                                pedido.user(),
                                pedido.password(),
                                pedido.path(),
                                pedido.query(),
                                pedido.protocol()));
        return ResponseEntity.status(permitido ? HttpStatus.OK : HttpStatus.UNAUTHORIZED)
                .build();
    }

    /** The password of {@code Authorization: Basic base64(user:password)}; the user name is not checked. */
    static Optional<String> claveBasica(String credenciales) {
        if (credenciales == null || !credenciales.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        try {
            String decodificadas = new String(
                    Base64.getDecoder().decode(credenciales.substring(6).trim()), StandardCharsets.UTF_8);
            int separador = decodificadas.indexOf(':');
            return separador < 0 ? Optional.empty() : Optional.of(decodificadas.substring(separador + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
