package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.application.port.ConsentimientoRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Consentimiento;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-09: the owner revokes the consent once the app has asked for confirmation (CA-09.2). The
 * {@code ConsentimientoRevocado} event stops capture in {@code camaras} and schedules the deletion of
 * every recording in {@code alertas} (CA-09.1), which tells the family when it is done (CA-09.3).
 */
@Service
public class RevocarConsentimiento {

    private final ConsentimientoRepository consentimientos;
    private final MiembroActual miembroActual;
    private final Clock reloj;

    public RevocarConsentimiento(ConsentimientoRepository consentimientos, MiembroActual miembroActual, Clock reloj) {
        this.consentimientos = consentimientos;
        this.miembroActual = miembroActual;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID usuarioId) {
        Membresia membresia = miembroActual.de(usuarioId);
        if (!membresia.esTitular()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
        Consentimiento consentimiento = consentimientos
                .ultimo()
                .filter(Consentimiento::vigente)
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_CONSENTIMIENTO));
        consentimiento.revocar(reloj.instant());
        consentimientos.guardar(consentimiento);
    }
}
