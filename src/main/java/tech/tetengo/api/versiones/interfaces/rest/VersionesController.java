package tech.tetengo.api.versiones.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;
import tech.tetengo.api.versiones.application.ConsultarVersiones;

/** {@code GET /api/versiones} (API contract §12, US-43). Public: it holds no personal data. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/versiones")
@Tag(name = "Versiones", description = "Latest published version of each app (US-43).")
class VersionesController {

    /** Clients check at startup and once a day; five minutes keeps a new release quick to reach them. */
    static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final ConsultarVersiones consultar;

    VersionesController(ConsultarVersiones consultar) {
        this.consultar = consultar;
    }

    @Operation(
            summary = "Published versions (public, US-43)",
            description =
                    "The latest version of Te Tengo Captura (windows, mac) and of the mobile app (apk, play, ios, pwa): download, SHA-256, size, date, «Qué cambió» notes and the minimum supported version (CA-43.1 to CA-43.8). A channel is null when nothing is published on it. Cache-Control public, max-age=300, with an ETag (If-None-Match → 304).")
    @SecurityRequirements
    @GetMapping(version = ApiVersioning.V1)
    ResponseEntity<VersionesResponse> consultar(
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String siNoCoincide) {
        VersionesResponse respuesta = VersionesMapper.aRespuesta(consultar.consultar());
        String etag = VersionesMapper.etag(respuesta);
        if (coincide(siNoCoincide, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CACHE)
                    .eTag(etag)
                    .build();
        }
        return ResponseEntity.ok().cacheControl(CACHE).eTag(etag).body(respuesta);
    }

    /** RFC 9110 §13.1.2: weak comparison, a list of tags or {@code *}. */
    static boolean coincide(String siNoCoincide, String etag) {
        if (siNoCoincide == null || siNoCoincide.isBlank()) {
            return false;
        }
        return Arrays.stream(siNoCoincide.split(","))
                .map(String::strip)
                .map(t -> t.startsWith("W/") ? t.substring(2) : t)
                .anyMatch(t -> t.equals("*") || t.equals(etag));
    }
}
