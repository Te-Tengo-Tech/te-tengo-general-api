package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.MonitoreoError;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * The viewer switches what the camera's stream shows (API contract §4): the video, the video with the
 * skeleton, or only the skeleton. The mode applies to the stream, so every viewer of the camera sees it.
 */
@Service
public class CambiarModoDeVistaEnVivo {

    private final AccesoVistaEnVivoRepository accesos;
    private final Transmisiones transmisiones;
    private final Clock reloj;

    public CambiarModoDeVistaEnVivo(AccesoVistaEnVivoRepository accesos, Transmisiones transmisiones, Clock reloj) {
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.reloj = reloj;
    }

    /** Only the session's viewer, while it is open; returns the stream's mode. */
    @Transactional
    public ModoDeVista ejecutar(UUID sesionId, UUID usuarioId, String modo) {
        ModoDeVista pedido = modo(modo);
        Instant ahora = reloj.instant();
        AccesoVistaEnVivo sesion = accesos.buscar(sesionId)
                .filter(acceso -> acceso.getUsuarioId().equals(usuarioId))
                .filter(acceso -> acceso.admiteLectura(ahora))
                .orElseThrow(() -> new ErrorDeNegocio(MonitoreoError.SESION_NO_ENCONTRADA));
        transmisiones.bloquear(sesion.getCamaraId());
        return transmisiones.asegurar(sesion.getCamaraId(), pedido, ahora);
    }

    /** {@code VIDEO}, {@code VIDEO_CON_POSTURA} or {@code SOLO_POSTURA}; anything else is {@code 400 VALIDACION}. */
    static ModoDeVista modo(String valor) {
        return ModoDeVista.desde(valor)
                .orElseThrow(() -> new ErrorDeNegocio(
                        MonitoreoError.VALIDACION,
                        Map.of("campos", Map.of("modo", "Elige VIDEO, VIDEO_CON_POSTURA o SOLO_POSTURA."))));
    }
}
