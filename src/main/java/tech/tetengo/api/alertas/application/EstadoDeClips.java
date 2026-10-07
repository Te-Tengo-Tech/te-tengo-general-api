package tech.tetengo.api.alertas.application;

import org.springframework.stereotype.Component;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoClip;

/**
 * The backend is not told when the agent's upload finishes, so a clip whose upload URL was issued is
 * confirmed by asking the storage, once; the alert remembers it. Call inside a transaction.
 */
@Component
public class EstadoDeClips {

    private final AlmacenamientoDeClips almacenamiento;

    public EstadoDeClips(AlmacenamientoDeClips almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    public EstadoClip actualizar(Alerta alerta) {
        if (alerta.estadoClip() == EstadoClip.NO_DISPONIBLE
                && alerta.getClipClave() != null
                && almacenamiento.existe(alerta.getClipClave())) {
            alerta.confirmarClipSubido();
        }
        return alerta.estadoClip();
    }
}
