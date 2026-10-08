package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;

/**
 * US-24 / CA-24.1: closing the live view records how long it lasted. Only its viewer closes it. The
 * camera stops streaming when it was the last open session.
 */
@Service
public class CerrarVistaEnVivo {

    private final AccesoVistaEnVivoRepository accesos;
    private final Transmisiones transmisiones;
    private final Clock reloj;

    public CerrarVistaEnVivo(AccesoVistaEnVivoRepository accesos, Transmisiones transmisiones, Clock reloj) {
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID sesionId, UUID usuarioId) {
        Instant ahora = reloj.instant();
        accesos.buscar(sesionId)
                .filter(acceso -> acceso.getUsuarioId().equals(usuarioId))
                .filter(acceso -> acceso.abierta())
                .ifPresent(acceso -> {
                    transmisiones.bloquear(acceso.getCamaraId());
                    transmisiones.finalizar(List.of(acceso), ahora);
                    transmisiones.detenerSiNoQuedanSesiones(acceso.getCamaraId());
                });
    }
}
