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
import tech.tetengo.api.camaras.application.CamarasDetectadas;
import tech.tetengo.api.camaras.application.DesvincularPc;
import tech.tetengo.api.camaras.application.ListarPcs;
import tech.tetengo.api.camaras.application.RenombrarPc;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-30, US-31: the household's PCs in the app. Invited members see them read-only (CA-30.2, CA-31.5). */
@RestController
@RequestMapping(ApiVersioning.BASE + "/pcs")
@Tag(name = "PCs y cámaras", description = "The household's PCs and cameras (US-28 to US-31).")
class PcController {

    private final ListarPcs listarPcs;
    private final RenombrarPc renombrarPc;
    private final DesvincularPc desvincularPc;
    private final CamarasDetectadas detectadas;

    PcController(
            ListarPcs listarPcs, RenombrarPc renombrarPc, DesvincularPc desvincularPc, CamarasDetectadas detectadas) {
        this.listarPcs = listarPcs;
        this.renombrarPc = renombrarPc;
        this.desvincularPc = desvincularPc;
        this.detectadas = detectadas;
    }

    @Operation(
            summary = "Ask the PC to look for webcams again: «Buscar de nuevo» (owner, US-29)",
            description =
                    "CA-29.6: the PC gets {\"buscarCamaras\":true} on its control channel and reports its webcams again; the app reads them with GET /api/pcs/{id}/camaras a few seconds later. 202; nothing happens while the PC is offline. Errors: 404 PC_NO_ENCONTRADA, 403 SOLO_TITULAR.")
    @PostMapping(path = "/{id}/busqueda-de-camaras", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.ACCEPTED)
    void buscarCamaras(@PathVariable UUID id) {
        UsuarioActual.exigirTitular();
        detectadas.buscarDeNuevo(id);
    }

    @Operation(
            summary = "The webcams a PC detected («Elige las cámaras», US-29)",
            description =
                    "Every webcam the PC reported, in use in a room or «sin usar», with whether it is plugged in. The owner assigns them with PUT /api/camaras/{id}/ambiente and can preview each one first (POST /api/camaras/{id}/vista-previa). Any member. Errors: 404 PC_NO_ENCONTRADA.")
    @GetMapping(path = "/{id}/camaras", version = ApiVersioning.V1)
    List<CamaraDetectadaResponse> camaras(@PathVariable UUID id) {
        return detectadas.deLaPc(id).stream().map(CamaraDetectadaResponse::de).toList();
    }

    @Operation(
            summary = "List the PCs with their cameras (US-30)",
            description =
                    "Each linked PC with its status (EN_LINEA, or DESCONECTADA since ultimaSenal), agent version and cameras (CA-30.2, CA-30.3). Any member.")
    @GetMapping(version = ApiVersioning.V1)
    List<PcResponse> listar() {
        return listarPcs.ejecutar().stream().map(PcMapper::aRespuesta).toList();
    }

    @Operation(
            summary = "Rename a PC (owner)",
            description =
                    "nombre 1-60 characters. Errors: 422 PC_NOMBRE_VACIO, 422 PC_NOMBRE_MUY_LARGO, 404 PC_NO_ENCONTRADA, 403 SOLO_TITULAR.")
    @PatchMapping(path = "/{id}", version = ApiVersioning.V1)
    PcResponse renombrar(@PathVariable UUID id, @Valid @RequestBody RenombrarPcRequest pedido) {
        UsuarioActual.exigirTitular();
        return PcMapper.aRespuesta(renombrarPc.ejecutar(id, pedido.nombre(), UsuarioActual.id()));
    }

    @Operation(
            summary = "Remove a PC: «Quitar esta PC» (owner, US-31)",
            description =
                    "The app stops listing it and its cameras at once; its tokens stop working, and the PC stops capturing and shows the link code again as soon as it hears it, at once if online (CA-31.1) or when it connects (CA-31.2). Errors: 404 PC_NO_ENCONTRADA, 403 SOLO_TITULAR (CA-31.5).")
    @DeleteMapping(path = "/{id}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desvincular(@PathVariable UUID id) {
        UsuarioActual.exigirTitular();
        desvincularPc.desdeLaApp(id, UsuarioActual.id());
    }
}
