package tech.tetengo.api.monitoreo.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.monitoreo.application.AutorizarMediaMtx;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/**
 * MediaMTX's HTTP authorization hook (ADR 0007). Only MediaMTX calls it, with the shared secret in the
 * query; the reverse proxy never routes {@code /api/interno/**}.
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
                    "MediaMTX authMethod http. 200 allows: publish on camaras/<camaraId> as user agente with the transmission's publish token; read or playback with ?token=<viewer token> of an open session of that camera. 401 for anything else, or without the right secreto.")
    @SecurityRequirements
    @PostMapping(path = ApiVersioning.BASE + "/interno/mediamtx/autorizar", version = ApiVersioning.V1)
    ResponseEntity<Void> autorizar(
            @RequestParam(required = false) String secreto,
            @RequestBody(required = false) AutorizacionMediaMtxRequest pedido) {
        boolean permitido = pedido != null
                && autorizar.ejecutar(
                        secreto,
                        new PeticionDeMediaMtx(
                                pedido.action(), pedido.user(), pedido.password(), pedido.path(), pedido.query()));
        return ResponseEntity.status(permitido ? HttpStatus.OK : HttpStatus.UNAUTHORIZED)
                .build();
    }
}
