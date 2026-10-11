package tech.tetengo.api.movimientos.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.movimientos.application.GestionarConsentimientoDeMovimientos;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-35: the consent to store movements, separate from the monitoring consent and optional. */
@RestController
@RequestMapping(ApiVersioning.BASE + "/hogar/consentimiento-movimientos")
@Tag(name = "Datos de movimiento", description = "Movement data: consent, cloud retention and replay (US-35 to US-39).")
class ConsentimientoDeMovimientosController {

    private final GestionarConsentimientoDeMovimientos consentimiento;

    ConsentimientoDeMovimientosController(GestionarConsentimientoDeMovimientos consentimiento) {
        this.consentimiento = consentimiento;
    }

    @Operation(
            summary = "Read the consent to store movements (US-35)",
            description =
                    "Whether it is in force, the text version to show, the proof of the latest one (CA-35.2) and the deletion after the latest revocation (CA-35.4). Any member.")
    @GetMapping(version = ApiVersioning.V1)
    ConsentimientoDeMovimientosResponse ver() {
        return ConsentimientoDeMovimientosResponse.de(consentimiento.consultar());
    }

    @Operation(
            summary = "Register the consent to store movements (owner, US-35)",
            description =
                    "Stores who gave it, who registered it, when and the text version (CA-35.2); movements may be stored from then on. Errors: 409 SIN_CONSENTIMIENTO (the monitoring consent comes first, CA-35.1), 422 CONSENTIMIENTO_NO_ACEPTADO, 422 VERSION_DE_TEXTO_INVALIDA, 400 VALIDACION, 403 SOLO_TITULAR.")
    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    ConsentimientoDeMovimientosResponse otorgar(@Valid @RequestBody ConsentimientoDeMovimientosRequest pedido) {
        UsuarioActual.exigirTitular();
        return ConsentimientoDeMovimientosResponse.de(consentimiento.otorgar(
                UsuarioActual.id(),
                pedido.otorgadoPor(),
                pedido.representante(),
                pedido.aceptado(),
                pedido.versionTexto()));
    }

    @Operation(
            summary = "Revoke the consent to store movements (owner, US-35)",
            description =
                    "Nothing more is stored; every cloud movement (and the clips' landmarks) is deleted and push MOVIMIENTOS_ELIMINADOS is sent when done (CA-35.4). The monitoring consent and the alerts stay. Answers how many files it deletes. Errors: 404 SIN_CONSENTIMIENTO_DE_MOVIMIENTOS, 403 SOLO_TITULAR.")
    @DeleteMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.ACCEPTED)
    EliminacionProgramadaResponse revocar() {
        UsuarioActual.exigirTitular();
        return new EliminacionProgramadaResponse(true, consentimiento.revocar(UsuarioActual.id()));
    }

    record EliminacionProgramadaResponse(boolean eliminacionProgramada, long archivos) {}
}
