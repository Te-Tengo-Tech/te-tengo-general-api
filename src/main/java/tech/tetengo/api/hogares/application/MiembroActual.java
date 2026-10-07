package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * The caller's membership in the household of the request (taken from the token, never from the
 * client). A removed member (CA-08.3) gets {@code 403 SIN_MEMBRESIA} even with a still-valid token.
 */
@Component
public class MiembroActual {

    private final MembresiaRepository membresias;

    public MiembroActual(MembresiaRepository membresias) {
        this.membresias = membresias;
    }

    public Membresia de(UUID usuarioId) {
        UUID hogarId = HogarActual.obtener().orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
        return membresias.buscar(hogarId, usuarioId).orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
    }
}
