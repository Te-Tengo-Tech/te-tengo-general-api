package tech.tetengo.api.hogares.application;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.MembresiasDeUsuario;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.model.Rol;

/** Tells {@code cuentas} which household a new session belongs to (see {@link MembresiasDeUsuario}). */
@Service
public class ConsultaDeMembresias implements MembresiasDeUsuario {

    private final MembresiaRepository membresias;

    public ConsultaDeMembresias(MembresiaRepository membresias) {
        this.membresias = membresias;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HogarDelUsuario> hogarPredeterminado(UUID usuarioId) {
        return membresias.delUsuario(usuarioId).stream()
                .min(Comparator.comparing((Membresia m) -> !m.esTitular()))
                .map(m -> new HogarDelUsuario(m.getHogarId(), m.getRol()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rol> rolEn(UUID usuarioId, UUID hogarId) {
        return membresias.buscar(hogarId, usuarioId).map(Membresia::getRol);
    }
}
