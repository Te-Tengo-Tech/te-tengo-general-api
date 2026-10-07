package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.application.port.SesionRepository;

/** US-02 / CA-02.4: signing out revokes the refresh token, so a new sign-in is required. */
@Service
public class CerrarSesion {

    private final SesionRepository sesiones;
    private final Clock reloj;

    public CerrarSesion(SesionRepository sesiones, Clock reloj) {
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID usuarioId, UUID sesionId) {
        sesiones.buscar(sesionId)
                .filter(sesion -> sesion.getUsuarioId().equals(usuarioId))
                .ifPresent(sesion -> sesion.cerrar(reloj.instant()));
    }
}
