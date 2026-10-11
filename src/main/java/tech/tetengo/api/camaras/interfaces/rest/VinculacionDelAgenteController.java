package tech.tetengo.api.camaras.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.CanjearTokenDePc;
import tech.tetengo.api.camaras.application.DesvincularPc;
import tech.tetengo.api.camaras.application.ErrorOAuth;
import tech.tetengo.api.camaras.application.PropiedadesDeVinculacion;
import tech.tetengo.api.camaras.application.SolicitarVinculacion;
import tech.tetengo.api.camaras.application.SolicitudDeVinculacion;
import tech.tetengo.api.camaras.application.TokensDePc;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * The PC link's OAuth endpoints (ADR 0009): device authorization (RFC 8628 §3.1), token (RFC 6749
 * §3.2 with the device code and refresh token grants) and revocation (RFC 7009). They follow the RFCs,
 * not the API's conventions: form-encoded requests ({@code application/x-www-form-urlencoded}),
 * snake_case JSON, RFC 6749 §5.2 errors and {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping(ApiVersioning.BASE + "/agente")
@Tag(name = "Vinculación de la PC", description = "PC link: OAuth 2.0 device grant (US-28, US-31, ADR 0009).")
class VinculacionDelAgenteController {

    private static final String FORMULARIO = MediaType.APPLICATION_FORM_URLENCODED_VALUE;

    private final SolicitarVinculacion solicitar;
    private final CanjearTokenDePc canjear;
    private final DesvincularPc desvincular;
    private final PropiedadesDeVinculacion propiedades;

    VinculacionDelAgenteController(
            SolicitarVinculacion solicitar,
            CanjearTokenDePc canjear,
            DesvincularPc desvincular,
            PropiedadesDeVinculacion propiedades) {
        this.solicitar = solicitar;
        this.canjear = canjear;
        this.desvincular = desvincular;
        this.propiedades = propiedades;
    }

    @Operation(
            summary = "Ask to link this PC (agent, RFC 8628 device authorization, US-28)",
            description =
                    "Form fields client_id (te-tengo-captura), nombre_pc (1-60), sistema_operativo and version_agente (optional). Answers device_code (secret), user_code (XXXX-XXXX, 10 min), verification_uri, verification_uri_complete (the QR), expires_in and interval (5 s). Errors (RFC 6749 §5.2): 400 invalid_request, 401 invalid_client.")
    @SecurityRequirements
    @PostMapping(path = "/vinculacion", consumes = FORMULARIO, version = ApiVersioning.V1)
    ResponseEntity<AutorizacionDeDispositivoResponse> solicitar(
            @RequestParam MultiValueMap<String, String> formulario) {
        SolicitudDeVinculacion solicitud = solicitar.ejecutar(
                formulario.getFirst("client_id"),
                formulario.getFirst("nombre_pc"),
                formulario.getFirst("sistema_operativo"),
                formulario.getFirst("version_agente"));
        return sinCache(new AutorizacionDeDispositivoResponse(
                solicitud.codigoDispositivo(),
                solicitud.codigoUsuario(),
                solicitud.uriVerificacion(),
                solicitud.uriVerificacionCompleta(),
                solicitud.expiraEnSegundos(),
                solicitud.intervaloSegundos()));
    }

    @Operation(
            summary = "Get or renew the PC's tokens (agent, RFC 6749 token endpoint, US-28)",
            description =
                    "grant_type urn:ietf:params:oauth:grant-type:device_code with device_code (poll every interval) or refresh_token with refresh_token; client_id always. Answers access_token, token_type Bearer, expires_in, refresh_token (rotates: use each once), pc_id and hogar_id. Errors (400 unless stated): authorization_pending, slow_down (+5 s), access_denied, expired_token, invalid_grant (unknown, used or revoked code or token; a rotated refresh token used again unlinks the PC), unsupported_grant_type, invalid_request, 401 invalid_client.")
    @SecurityRequirements
    @PostMapping(path = "/token", consumes = FORMULARIO, version = ApiVersioning.V1)
    ResponseEntity<?> token(@RequestParam MultiValueMap<String, String> formulario) {
        CanjearTokenDePc.Respuesta respuesta = canjear.ejecutar(
                formulario.getFirst("grant_type"),
                formulario.getFirst("client_id"),
                formulario.getFirst("device_code"),
                formulario.getFirst("refresh_token"));
        return switch (respuesta) {
            case CanjearTokenDePc.Emitidos(TokensDePc tokens) ->
                sinCache(new TokenResponse(
                        tokens.tokenAcceso(),
                        "Bearer",
                        tokens.expiraEnSegundos(),
                        tokens.tokenRenovacion(),
                        tokens.pcId(),
                        tokens.hogarId()));
            case CanjearTokenDePc.Rechazada(ErrorOAuth error) -> errorOAuth(error);
        };
    }

    @Operation(
            summary = "Revoke the PC's refresh token, unlinking it (agent, RFC 7009, US-31)",
            description =
                    "Form fields token (the refresh token), token_type_hint (optional) and client_id. Revoking it unlinks the PC and notifies the family (CA-31.3). Always 200, also for unknown tokens (RFC 7009 §2.2). Errors: 400 invalid_request, 401 invalid_client.")
    @SecurityRequirements
    @PostMapping(path = "/token/revocacion", consumes = FORMULARIO, version = ApiVersioning.V1)
    ResponseEntity<Void> revocar(@RequestParam MultiValueMap<String, String> formulario) {
        desvincular.revocandoToken(formulario.getFirst("client_id"), formulario.getFirst("token"), propiedades);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(ErrorOAuth.class)
    ResponseEntity<ErrorOAuthResponse> errorOAuth(ErrorOAuth error) {
        return ResponseEntity.status(error.estado())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorOAuthResponse(error.codigo(), error.descripcion()));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo);
    }

    /** RFC 8628 §3.2. */
    record AutorizacionDeDispositivoResponse(
            @JsonProperty("device_code") String deviceCode,
            @JsonProperty("user_code") String userCode,
            @JsonProperty("verification_uri") URI verificationUri,
            @JsonProperty("verification_uri_complete") URI verificationUriComplete,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("interval") long interval) {}

    /** RFC 6749 §5.1, plus the PC and its household. */
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("pc_id") UUID pcId,
            @JsonProperty("hogar_id") UUID hogarId) {}

    /** RFC 6749 §5.2. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ErrorOAuthResponse(
            @JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription) {}
}
