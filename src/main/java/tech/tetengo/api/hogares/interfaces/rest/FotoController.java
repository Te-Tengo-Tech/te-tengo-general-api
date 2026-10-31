package tech.tetengo.api.hogares.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tech.tetengo.api.hogares.application.ConfirmarSubidaDeFoto;
import tech.tetengo.api.hogares.application.QuitarFoto;
import tech.tetengo.api.hogares.application.SolicitarSubidaDeFoto;
import tech.tetengo.api.hogares.application.SubidaPedida;
import tech.tetengo.api.hogares.domain.model.DestinoDeFoto;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** API contract §13: photos of the caller and of the older adult («Cambiar foto», «Quitar la foto»). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/fotos")
@Tag(name = "Fotos", description = "Profile photos of the members and of the older adult (API contract §13).")
class FotoController {

    private final SolicitarSubidaDeFoto solicitarSubida;
    private final ConfirmarSubidaDeFoto confirmarSubida;
    private final QuitarFoto quitarFoto;

    FotoController(
            SolicitarSubidaDeFoto solicitarSubida, ConfirmarSubidaDeFoto confirmarSubida, QuitarFoto quitarFoto) {
        this.solicitarSubida = solicitarSubida;
        this.confirmarSubida = confirmarSubida;
        this.quitarFoto = quitarFoto;
    }

    @Operation(
            summary = "Ask for a photo upload",
            description =
                    "A pre-signed PUT valid 10 minutes for exactly that type and size. Errors: 403 SOLO_TITULAR (ADULTO_MAYOR by an INVITADO), 413 FOTO_MUY_GRANDE, 415 FOTO_FORMATO_NO_ADMITIDO, 400 VALIDACION.")
    @PostMapping(path = "/subidas", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SubidaDeFotoResponse solicitar(@Valid @RequestBody SolicitarSubidaDeFotoRequest pedido) {
        SubidaPedida subida = solicitarSubida.ejecutar(
                UsuarioActual.id(), DestinoDeFoto.valueOf(pedido.de()), pedido.tipo(), pedido.tamanoBytes());
        return new SubidaDeFotoResponse(subida.subidaId(), subida.url(), subida.cabeceras(), subida.expiraEn());
    }

    @Operation(
            summary = "Confirm a photo upload",
            description =
                    "Checks the image, stores it as a square JPEG of 512 x 512 at most without metadata and replaces the photo. Errors: 404 SUBIDA_NO_ENCONTRADA, 410 SUBIDA_VENCIDA, 413 FOTO_MUY_GRANDE, 415 FOTO_FORMATO_NO_ADMITIDO, 403 SOLO_TITULAR.")
    @PostMapping(path = "/subidas/{subidaId}/confirmacion", version = ApiVersioning.V1)
    Foto confirmar(@PathVariable UUID subidaId) {
        return confirmarSubida.ejecutar(UsuarioActual.id(), subidaId);
    }

    @Operation(
            summary = "Remove a photo",
            description =
                    "de = yo | adulto-mayor (owner only). Removing a missing photo also answers 204. Errors: 403 SOLO_TITULAR.")
    @DeleteMapping(path = "/{de}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void quitar(@PathVariable String de) {
        DestinoDeFoto destino = switch (de) {
            case "yo" -> DestinoDeFoto.YO;
            case "adulto-mayor" -> DestinoDeFoto.ADULTO_MAYOR;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        quitarFoto.ejecutar(UsuarioActual.id(), destino);
    }
}
