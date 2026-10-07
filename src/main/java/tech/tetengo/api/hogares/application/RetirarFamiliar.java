package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.FamiliarRetirado;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-08 / CA-08.3: the owner removes a family member, who loses access to the household's alerts at
 * once. The owner cannot be removed.
 */
@Service
public class RetirarFamiliar {

    private final MembresiaRepository membresias;
    private final MiembroActual miembroActual;
    private final ApplicationEventPublisher eventos;

    public RetirarFamiliar(
            MembresiaRepository membresias, MiembroActual miembroActual, ApplicationEventPublisher eventos) {
        this.membresias = membresias;
        this.miembroActual = miembroActual;
        this.eventos = eventos;
    }

    @Transactional
    public void ejecutar(UUID usuarioId, UUID familiarId) {
        Membresia propia = miembroActual.de(usuarioId);
        if (!propia.esTitular()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
        membresias.buscar(propia.getHogarId(), familiarId).ifPresent(familiar -> {
            if (familiar.esTitular()) {
                throw new ErrorDeNegocio(HogarError.NO_SE_PUEDE_RETIRAR_TITULAR);
            }
            membresias.eliminar(familiar);
            eventos.publishEvent(new FamiliarRetirado(propia.getHogarId(), familiarId));
        });
    }
}
