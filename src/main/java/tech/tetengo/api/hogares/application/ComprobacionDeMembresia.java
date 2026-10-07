package tech.tetengo.api.hogares.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.shared.application.port.ComprobadorDeMembresia;

/** CA-08.3: a removed family member loses access at once, even with a token that has not expired. */
@Service
public class ComprobacionDeMembresia implements ComprobadorDeMembresia {

    private final MembresiaRepository membresias;

    public ComprobacionDeMembresia(MembresiaRepository membresias) {
        this.membresias = membresias;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esMiembro(UUID hogarId, UUID usuarioId) {
        return membresias.buscar(hogarId, usuarioId).isPresent();
    }
}
