package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.hogares.application.ConsultarConsentimiento;
import tech.tetengo.api.hogares.application.OtorgarConsentimiento;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/hogar/consentimiento")
class ConsentimientoController {

    private final OtorgarConsentimiento otorgarConsentimiento;
    private final ConsultarConsentimiento consultarConsentimiento;

    ConsentimientoController(
            OtorgarConsentimiento otorgarConsentimiento, ConsultarConsentimiento consultarConsentimiento) {
        this.otorgarConsentimiento = otorgarConsentimiento;
        this.consultarConsentimiento = consultarConsentimiento;
    }

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

    @GetMapping(version = ApiVersioning.V1)
    ConsentimientoResponse consultar() {
        return HogarMapper.aRespuesta(consultarConsentimiento.ejecutar(UsuarioActual.id()));
    }
}
