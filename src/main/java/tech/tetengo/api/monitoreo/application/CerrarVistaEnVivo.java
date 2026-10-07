package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;

/** US-24 / CA-24.1: closing the live view records how long it lasted. Only its viewer closes it. */
@Service
public class CerrarVistaEnVivo {

    private final AccesoVistaEnVivoRepository accesos;
    private final Clock reloj;

    public CerrarVistaEnVivo(AccesoVistaEnVivoRepository accesos, Clock reloj) {
        this.accesos = accesos;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID sesionId, UUID usuarioId) {
        accesos.buscar(sesionId)
                .filter(acceso -> acceso.getUsuarioId().equals(usuarioId))
                .ifPresent(acceso -> acceso.cerrar(reloj.instant()));
    }
}
