package tech.tetengo.api.camaras.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.GestionarAmbientes;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-29: the household's rooms («ambientes»; the apps say «habitación»). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/ambientes")
@Tag(name = "PCs y cámaras", description = "The household's PCs and cameras (US-28 to US-31).")
class AmbienteController {

    private static final String NOMBRE =
            " nombre 1-40 characters, unique in the household ignoring case. Errors: 422 AMBIENTE_NOMBRE_VACIO, 422 AMBIENTE_NOMBRE_MUY_LARGO, 409 AMBIENTE_YA_EXISTE, 403 SOLO_TITULAR.";

    private final GestionarAmbientes ambientes;

    AmbienteController(GestionarAmbientes ambientes) {
        this.ambientes = ambientes;
    }

    @Operation(
            summary = "List the rooms (US-29)",
            description = "Each room with its cameras in use (at most one, CA-29.3), by name. Any member.")
    @GetMapping(version = ApiVersioning.V1)
    List<AmbienteResponse> listar() {
        return ambientes.listar().stream().map(AmbienteResponse::de).toList();
    }

    @Operation(summary = "Create a room (owner, US-29)", description = "Returns the Ambiente." + NOMBRE)
    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    AmbienteResponse crear(@Valid @RequestBody NombreDeAmbienteRequest pedido) {
        UsuarioActual.exigirTitular();
        return AmbienteResponse.de(ambientes.crear(pedido.nombre()));
    }

    @Operation(
            summary = "Rename a room (owner, US-29)",
            description =
                    "Its cameras and their later alerts use the new name (CA-29.2). Errors also 404 AMBIENTE_NO_ENCONTRADO."
                            + NOMBRE)
    @PatchMapping(path = "/{id}", version = ApiVersioning.V1)
    AmbienteResponse renombrar(@PathVariable UUID id, @Valid @RequestBody NombreDeAmbienteRequest pedido) {
        UsuarioActual.exigirTitular();
        return AmbienteResponse.de(ambientes.renombrar(id, pedido.nombre(), UsuarioActual.id()));
    }

    @Operation(
            summary = "Delete a room (owner, US-29)",
            description =
                    "Only without a camera in use. Errors: 409 AMBIENTE_CON_CAMARA, 404 AMBIENTE_NO_ENCONTRADO, 403 SOLO_TITULAR.")
    @DeleteMapping(path = "/{id}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable UUID id) {
        UsuarioActual.exigirTitular();
        ambientes.eliminar(id);
    }
}
