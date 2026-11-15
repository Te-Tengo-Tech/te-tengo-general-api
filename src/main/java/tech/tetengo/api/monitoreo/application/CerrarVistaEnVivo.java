package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

/**
 * US-24 / CA-24.1: closing the live view records how long it lasted. Only its viewer closes it. The
 * camera stops streaming when it was the last open session. Closing another member's or another
 * household's session changes nothing, still answers as if it worked, and is recorded as a denial (ADR
 * 0015).
 */
@Service
public class CerrarVistaEnVivo {

    private final AccesoVistaEnVivoRepository accesos;
    private final Transmisiones transmisiones;
    private final RegistroDeAccesosDenegados denegados;
    private final Clock reloj;

    public CerrarVistaEnVivo(
            AccesoVistaEnVivoRepository accesos,
            Transmisiones transmisiones,
            RegistroDeAccesosDenegados denegados,
            Clock reloj) {
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.denegados = denegados;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID sesionId, UUID usuarioId) {
        Instant ahora = reloj.instant();
        Optional<AccesoVistaEnVivo> propia =
                accesos.buscar(sesionId).filter(acceso -> acceso.getUsuarioId().equals(usuarioId));
        if (propia.isEmpty()) {
            denegados.siLaSesionEsAjena(sesionId, usuarioId);
            return;
        }
        propia.filter(AccesoVistaEnVivo::abierta).ifPresent(acceso -> {
            transmisiones.bloquear(acceso.getCamaraId());
            transmisiones.finalizar(List.of(acceso), ahora);
            transmisiones.detenerSiNoQuedanSesiones(acceso.getCamaraId());
        });
    }
}
