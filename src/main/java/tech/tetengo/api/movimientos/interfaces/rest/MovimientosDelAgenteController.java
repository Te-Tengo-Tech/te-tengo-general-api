package tech.tetengo.api.movimientos.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.movimientos.application.EstadoDeMovimientos;
import tech.tetengo.api.movimientos.application.PropiedadesDeMovimientos;
import tech.tetengo.api.movimientos.application.Rangos;
import tech.tetengo.api.movimientos.application.ServicioDeDatosDeMovimiento;
import tech.tetengo.api.movimientos.application.SubidaDeMovimientos;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-37 on the PC: uploading movement files (AGENT_CONTRACT.md, "Movement data"). */
@RestController
@Tag(name = "Agente del hogar", description = "Endpoints of the household agent (AGENT_CONTRACT.md).")
class MovimientosDelAgenteController {

    private static final String API = ApiVersioning.BASE + "/agente/movimientos";

    private final SubidaDeMovimientos subida;
    private final ServicioDeDatosDeMovimiento datos;
    private final PropiedadesDeMovimientos propiedades;
    private final Clock reloj;

    MovimientosDelAgenteController(
            SubidaDeMovimientos subida,
            ServicioDeDatosDeMovimiento datos,
            PropiedadesDeMovimientos propiedades,
            Clock reloj) {
        this.subida = subida;
        this.datos = datos;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Operation(
            summary = "May movements be stored? (agent, US-35, US-37)",
            description =
                    "consentimientoVigente and guardarEnLaPc (CA-35.3: nothing is stored without it), guardarEnLaNube and retencionDias (CA-37.1). Camera or PC token.")
    @GetMapping(path = API + "/estado", version = ApiVersioning.V1)
    EstadoDeMovimientos estado() {
        return datos.estadoParaElAgente();
    }

    @Operation(
            summary = "Get the upload URL of a movement file (agent, US-37)",
            description =
                    "One Parquet+zstd file per camera every 5 minutes (CA-37.2): pre-signed PUT URL to object storage and the headers the PUT carries; then confirm it. Errors: 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS, 409 MOVIMIENTOS_EN_LA_NUBE_DESACTIVADOS, 422 MOVIMIENTOS_FUERA_DEL_PLAZO (drop it), 409 ARCHIVO_YA_SUBIDO {archivoId}, 400 VALIDACION. Camera token.")
    @PostMapping(path = API, version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    SubidaResponse preparar(@Valid @RequestBody ArchivoRequest pedido) {
        Rangos.exigir(pedido.desde(), pedido.hasta(), propiedades.duracionMaximaArchivo(), reloj.instant());
        if (pedido.tamanoBytes() > propiedades.tamanoMaximo().toBytes()) {
            throw Rangos.invalido("tamanoBytes", "El archivo es demasiado grande.");
        }
        var s = subida.preparar(
                UsuarioActual.camaraDelAgente(),
                pedido.desde(),
                pedido.hasta(),
                pedido.tamanoBytes(),
                pedido.sha256(),
                pedido.fotogramas());
        return new SubidaResponse(s.archivoId(), s.url(), s.cabeceras(), s.expiraEn());
    }

    @Operation(
            summary = "Confirm a movement file was uploaded (agent, US-37)",
            description =
                    "It enters the index if the storage has it. Errors: 409 ARCHIVO_NO_SUBIDO (upload it again), 404 ARCHIVO_NO_ENCONTRADO, 409 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS (revoked meanwhile: the object is deleted). Camera token.")
    @PostMapping(path = API + "/{archivoId}/confirmacion", version = ApiVersioning.V1)
    ConfirmacionResponse confirmar(@PathVariable UUID archivoId) {
        var archivo = subida.confirmar(UsuarioActual.camaraDelAgente(), archivoId);
        return new ConfirmacionResponse(archivo.getId(), archivo.getEstado().name(), archivo.getTamanoBytes());
    }

    record ArchivoRequest(
            @NotNull(message = "Falta el inicio.") Instant desde,
            @NotNull(message = "Falta el fin.") Instant hasta,
            @NotNull @Positive Long tamanoBytes,

            @NotNull @Pattern(regexp = "[0-9a-fA-F]{64}", message = "SHA-256 en hexadecimal.")
            String sha256,

            @NotNull @Min(0) Integer fotogramas) {}

    record SubidaResponse(UUID archivoId, URI urlSubida, Map<String, String> cabeceras, Instant expiraEn) {}

    record ConfirmacionResponse(UUID archivoId, String estado, long tamanoBytes) {}
}
