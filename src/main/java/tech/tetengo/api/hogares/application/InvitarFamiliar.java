package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.hogares.application.port.InvitacionRepository;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Invitacion;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;
import tech.tetengo.api.shared.application.port.NotificadorCorreo.Correo;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/** US-08 / CA-08.1: the owner invites another family member, who gets a link by e-mail. */
@Service
public class InvitarFamiliar {

    private final InvitacionRepository invitaciones;
    private final MembresiaRepository membresias;
    private final MiembroActual miembroActual;
    private final DirectorioDeUsuarios usuarios;
    private final NotificadorCorreo correo;
    private final PropiedadesDeInvitaciones propiedades;
    private final String enlace;
    private final Clock reloj;

    public InvitarFamiliar(
            InvitacionRepository invitaciones,
            MembresiaRepository membresias,
            MiembroActual miembroActual,
            DirectorioDeUsuarios usuarios,
            NotificadorCorreo correo,
            PropiedadesDeInvitaciones propiedades,
            @Value("${tetengo.enlaces.invitacion}") String enlace,
            Clock reloj) {
        this.invitaciones = invitaciones;
        this.membresias = membresias;
        this.miembroActual = miembroActual;
        this.usuarios = usuarios;
        this.correo = correo;
        this.propiedades = propiedades;
        this.enlace = enlace;
        this.reloj = reloj;
    }

    @Transactional
    public Invitacion ejecutar(UUID usuarioId, String direccion) {
        Membresia membresia = miembroActual.de(usuarioId);
        if (!membresia.esTitular()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
        boolean yaEsFamiliar = usuarios.buscarPorCorreo(direccion)
                .flatMap(u -> membresias.buscar(membresia.getHogarId(), u.id()))
                .isPresent();
        if (yaEsFamiliar) {
            throw new ErrorDeNegocio(HogarError.YA_ES_FAMILIAR);
        }
        String token = Secretos.generar();
        Invitacion invitacion = invitaciones.guardar(new Invitacion(
                membresia.getHogarId(),
                direccion,
                Secretos.huella(token),
                usuarioId,
                reloj.instant(),
                propiedades.vigencia()));
        String quienInvita = usuarios.buscar(usuarioId).map(u -> u.nombre()).orElse("Un familiar");
        correo.enviar(new Correo(
                invitacion.getCorreo(), "Te Tengo: te invitaron a recibir las alertas de tu familiar", """
                Hola:

                %s te invitó a Te Tengo para recibir y atender las alertas de su familiar.
                Abre este enlace en tu celular para crear tu acceso:

                %s
                """.formatted(
                                quienInvita, enlace.replace("{token}", token))));
        return invitacion;
    }
}
