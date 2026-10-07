package tech.tetengo.api.hogares.interfaces.rest;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.hogares.application.AceptarInvitacion;
import tech.tetengo.api.hogares.application.InvitarFamiliar;
import tech.tetengo.api.hogares.application.ListarFamiliares;
import tech.tetengo.api.hogares.application.RetirarFamiliar;
import tech.tetengo.api.hogares.domain.model.Invitacion;
import tech.tetengo.api.shared.infrastructure.security.UsuarioActual;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

/** US-08: invitations and family members. */
@RestController
class FamiliaController {

    private static final String API = ApiVersioning.BASE;

    private final InvitarFamiliar invitarFamiliar;
    private final AceptarInvitacion aceptarInvitacion;
    private final ListarFamiliares listarFamiliares;
    private final RetirarFamiliar retirarFamiliar;
    private final SmartValidator validador;

    FamiliaController(
            InvitarFamiliar invitarFamiliar,
            AceptarInvitacion aceptarInvitacion,
            ListarFamiliares listarFamiliares,
            RetirarFamiliar retirarFamiliar,
            SmartValidator validador) {
        this.invitarFamiliar = invitarFamiliar;
        this.aceptarInvitacion = aceptarInvitacion;
        this.listarFamiliares = listarFamiliares;
        this.retirarFamiliar = retirarFamiliar;
        this.validador = validador;
    }

    @PostMapping(path = API + "/invitaciones", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.CREATED)
    InvitacionResponse invitar(@Valid @RequestBody InvitarFamiliarRequest pedido) {
        UsuarioActual.exigirTitular();
        Invitacion invitacion = invitarFamiliar.ejecutar(UsuarioActual.id(), pedido.correo());
        return new InvitacionResponse(invitacion.getId(), invitacion.getCorreo(), invitacion.getExpiraEn());
    }

    /**
     * Public: a new account sends {@code {nombre, contrasena}}; an existing account sends its bearer
     * token instead.
     */
    @PostMapping(path = API + "/invitaciones/{token}/aceptacion", version = ApiVersioning.V1)
    ResponseEntity<Sesion> aceptar(
            @PathVariable String token,
            @RequestBody(required = false) AceptarInvitacionRequest pedido,
            Authentication autenticacion)
            throws MethodArgumentNotValidException, NoSuchMethodException {
        Sesion sesion;
        if (autenticacion instanceof JwtAuthenticationToken) {
            sesion = aceptarInvitacion.conCuentaExistente(
                    token, UsuarioActual.id(), UsuarioActual.sesionId().orElse(null));
        } else {
            validar(pedido == null ? new AceptarInvitacionRequest(null, null) : pedido);
            sesion = aceptarInvitacion.conCuentaNueva(token, pedido.nombre(), pedido.contrasena());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(sesion);
    }

    @GetMapping(path = API + "/familiares", version = ApiVersioning.V1)
    List<FamiliarResponse> listar() {
        return listarFamiliares.ejecutar(UsuarioActual.id()).stream()
                .map(f -> new FamiliarResponse(
                        f.usuarioId(), f.nombre(), f.correo(), f.rol().name()))
                .toList();
    }

    @DeleteMapping(path = API + "/familiares/{usuarioId}", version = ApiVersioning.V1)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirar(@PathVariable UUID usuarioId) {
        UsuarioActual.exigirTitular();
        retirarFamiliar.ejecutar(UsuarioActual.id(), usuarioId);
    }

    /** The body is optional (existing accounts), so it is validated here only for new accounts. */
    private void validar(AceptarInvitacionRequest pedido)
            throws MethodArgumentNotValidException, NoSuchMethodException {
        var errores = new BeanPropertyBindingResult(pedido, "pedido");
        validador.validate(pedido, errores);
        if (errores.hasErrors()) {
            var metodo = FamiliaController.class.getDeclaredMethod(
                    "aceptar", String.class, AceptarInvitacionRequest.class, Authentication.class);
            throw new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(metodo, 1), errores);
        }
    }
}
