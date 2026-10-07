package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.AltaDeCuentas;
import tech.tetengo.api.cuentas.ServicioDeSesiones;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.hogares.application.port.InvitacionRepository;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Invitacion;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-08 / CA-08.2: the invitee accepts, with a new account (name and password; the e-mail is the
 * invitation's) or with the account they are signed in with, and is linked to the household as
 * {@code INVITADO}, which enables their alerts.
 */
@Service
public class AceptarInvitacion {

    private final InvitacionRepository invitaciones;
    private final MembresiaRepository membresias;
    private final AltaDeCuentas cuentas;
    private final ServicioDeSesiones sesiones;
    private final Clock reloj;

    public AceptarInvitacion(
            InvitacionRepository invitaciones,
            MembresiaRepository membresias,
            AltaDeCuentas cuentas,
            ServicioDeSesiones sesiones,
            Clock reloj) {
        this.invitaciones = invitaciones;
        this.membresias = membresias;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    /** With a new account. */
    @Transactional
    public Sesion conCuentaNueva(String token, String nombre, String contrasena) {
        Invitacion invitacion = vigente(token);
        UUID usuarioId = cuentas.registrar(invitacion.getCorreo(), contrasena, nombre);
        return vincular(invitacion, usuarioId, null);
    }

    /** With the account of the bearer token. */
    @Transactional
    public Sesion conCuentaExistente(String token, UUID usuarioId, UUID sesionActual) {
        return vincular(vigente(token), usuarioId, sesionActual);
    }

    /** Checked (and used up) before anything else, so an invalid link never creates an account. */
    private Invitacion vigente(String token) {
        Invitacion invitacion = invitaciones
                .buscarPorToken(Secretos.huella(token))
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.INVITACION_VENCIDA));
        invitacion.aceptar(reloj.instant());
        return invitacion;
    }

    private Sesion vincular(Invitacion invitacion, UUID usuarioId, UUID sesionActual) {
        if (membresias.buscar(invitacion.getHogarId(), usuarioId).isPresent()) {
            throw new ErrorDeNegocio(HogarError.YA_ES_FAMILIAR);
        }
        membresias.guardar(new Membresia(invitacion.getHogarId(), usuarioId, Rol.INVITADO));
        invitaciones.guardar(invitacion);
        return sesiones.abrir(usuarioId, invitacion.getHogarId(), Rol.INVITADO, sesionActual);
    }
}
