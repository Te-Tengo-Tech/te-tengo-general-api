package tech.tetengo.api.alertas.application;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.EliminacionDeGrabacionesRepository;
import tech.tetengo.api.shared.application.port.EliminacionesDeGrabaciones;

/**
 * {@link EliminacionesDeGrabaciones} for {@code hogares}: the household's deletions after a consent
 * revocation (US-09, CA-09.3) and the recordings still stored.
 */
@Service
public class EliminacionesDelHogar implements EliminacionesDeGrabaciones {

    private final EliminacionDeGrabacionesRepository eliminaciones;
    private final AlertaRepository alertas;

    public EliminacionesDelHogar(EliminacionDeGrabacionesRepository eliminaciones, AlertaRepository alertas) {
        this.eliminaciones = eliminaciones;
        this.alertas = alertas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Eliminacion> ultima() {
        return eliminaciones
                .ultima()
                .map(e -> new Eliminacion(e.getSolicitadaEn(), e.getCompletadaEn(), e.getClipsEliminados()));
    }

    @Override
    @Transactional(readOnly = true)
    public long clipsGuardados() {
        return alertas.contarConClip();
    }
}
