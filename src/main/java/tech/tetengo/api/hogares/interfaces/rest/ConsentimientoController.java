package tech.tetengo.api.hogares.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.hogares.application.ConsultarConsentimiento;
import tech.tetengo.api.hogares.application.OtorgarConsentimiento;
import tech.tetengo.api.hogares.application.RevocarConsentimiento;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/hogar/consentimiento")
@Tag(name = "Consentimiento", description = "Consent of the older adult and its revocation (US-05, US-09).")
class ConsentimientoController {

    private final OtorgarConsentimiento otorgarConsentimiento;
    private final ConsultarConsentimiento consultarConsentimiento;
    private final RevocarConsentimiento revocarConsentimiento;

    ConsentimientoController(
            OtorgarConsentimiento otorgarConsentimiento,
            ConsultarConsentimiento consultarConsentimiento,
            RevocarConsentimiento revocarConsentimiento) {
        this.otorgarConsentimiento = otorgarConsentimiento;
        this.consultarConsentimiento = consultarConsentimiento;
        this.revocarConsentimiento = revocarConsentimiento;
    }

    @Operation(
            summary = "Register the consent (owner, US-05)",
            description =
                    "Stores the date and time (CA-05.3); capture may start (CA-05.1). Errors: 422 CONSENTIMIENTO_NO_ACEPTADO, both flags must be true (CA-05.4); 403 SOLO_TITULAR.")
    @PostMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    ConsentimientoResponse otorgar(@Valid @RequestBody OtorgarConsentimientoRequest pedido) {
        UsuarioActual.exigirTitular();
        return HogarMapper.aRespuesta(otorgarConsentimiento.ejecutar(
                UsuarioActual.id(),
                pedido.otorgadoPor(),
                pedido.aceptadoPorAdultoMayor(),
                pedido.vistaEnVivoAceptada()));
    }

    /** CA-09.1: {@code 202}, the recordings are deleted afterwards. */
    @Operation(
            summary = "Revoke the consent (owner, US-09)",
            description =
                    "202 {eliminacionProgramada: true}: capture stops and every recording is deleted (CA-09.1); push DATOS_ELIMINADOS when done (CA-09.3). Errors: 404 SIN_CONSENTIMIENTO, 403 SOLO_TITULAR.")
    @DeleteMapping(version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Boolean> revocar() {
        UsuarioActual.exigirTitular();
        revocarConsentimiento.ejecutar(UsuarioActual.id());
        return Map.of("eliminacionProgramada", true);
    }

    @Operation(summary = "Read the consent (US-05)", description = "Errors: 404 SIN_CONSENTIMIENTO (CA-05.2).")
    @GetMapping(version = ApiVersioning.V1)
    ConsentimientoResponse consultar() {
        return HogarMapper.aRespuesta(consultarConsentimiento.ejecutar(UsuarioActual.id()));
    }
}
