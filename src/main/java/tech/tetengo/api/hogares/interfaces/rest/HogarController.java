package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.hogares.application.ActualizarAdultoMayor;
import tech.tetengo.api.hogares.application.CambiarDeHogar;
import tech.tetengo.api.hogares.application.ConsultarHogar;
import tech.tetengo.api.hogares.application.ListarHogaresDelUsuario;
import tech.tetengo.api.hogares.application.RegistrarHogar;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
class HogarController {

    private static final String API = ApiVersioning.BASE;

    private final RegistrarHogar registrarHogar;
    private final ConsultarHogar consultarHogar;
    private final ActualizarAdultoMayor actualizarAdultoMayor;
    private final ListarHogaresDelUsuario listarHogares;
    private final CambiarDeHogar cambiarDeHogar;

    HogarController(
            RegistrarHogar registrarHogar,
            ConsultarHogar consultarHogar,
            ActualizarAdultoMayor actualizarAdultoMayor,
            ListarHogaresDelUsuario listarHogares,
            CambiarDeHogar cambiarDeHogar) {
        this.registrarHogar = registrarHogar;
        this.consultarHogar = consultarHogar;
        this.actualizarAdultoMayor = actualizarAdultoMayor;
        this.listarHogares = listarHogares;
        this.cambiarDeHogar = cambiarDeHogar;
    }

    @PostMapping(path = API + "/hogar", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    Sesion registrar(@Valid @RequestBody RegistrarHogarRequest pedido) {
        return registrarHogar.ejecutar(
                UsuarioActual.id(),
                HogarMapper.aDominio(pedido.adultoMayor()),
                UsuarioActual.sesionId().orElse(null));
    }

    @GetMapping(path = API + "/hogar", version = ApiVersioning.V1)
    HogarResponse consultar() {
        return HogarMapper.aRespuesta(consultarHogar.ejecutar(UsuarioActual.id()));
    }

    @PutMapping(path = API + "/hogar/adulto-mayor", version = ApiVersioning.V1)
    AdultoMayorResponse actualizarAdultoMayor(@Valid @RequestBody AdultoMayorRequest pedido) {
        UsuarioActual.exigirTitular();
        return HogarMapper.aRespuesta(actualizarAdultoMayor.ejecutar(UsuarioActual.id(), HogarMapper.aDominio(pedido)));
    }

    @GetMapping(path = API + "/hogares", version = ApiVersioning.V1)
    List<HogarDelUsuarioResponse> listar() {
        return listarHogares.ejecutar(UsuarioActual.id()).stream()
                .map(HogarMapper::aRespuesta)
                .toList();
    }

    @PostMapping(path = API + "/sesiones/hogar", version = ApiVersioning.V1)
    Sesion cambiar(@Valid @RequestBody CambiarHogarRequest pedido) {
        return cambiarDeHogar.ejecutar(
                UsuarioActual.id(), pedido.hogarId(), UsuarioActual.sesionId().orElse(null));
    }
}
