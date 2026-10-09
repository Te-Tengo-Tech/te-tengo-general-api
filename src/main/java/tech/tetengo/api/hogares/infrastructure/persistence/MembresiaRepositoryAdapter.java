package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;

@Repository
class MembresiaRepositoryAdapter implements MembresiaRepository {

    private final MembresiaJpaRepository jpa;

    MembresiaRepositoryAdapter(MembresiaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Membresia guardar(Membresia membresia) {
        try {
            return jpa.saveAndFlush(membresia);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous registrations: the "one owned household per account" index decides.
            throw new ErrorDeNegocio(HogarError.HOGAR_YA_REGISTRADO);
        }
    }

    @Override
    public Optional<Membresia> buscar(UUID hogarId, UUID usuarioId) {
        return jpa.findByHogarIdAndUsuarioId(hogarId, usuarioId);
    }

    @Override
    public List<Membresia> delUsuario(UUID usuarioId) {
        return jpa.findByUsuarioIdOrderByCreadoEnAsc(usuarioId);
    }

    @Override
    public List<Membresia> delHogar(UUID hogarId) {
        return jpa.findByHogarIdOrderByCreadoEnAsc(hogarId);
    }

    @Override
    public boolean esTitularDeAlgunHogar(UUID usuarioId) {
        return jpa.existsByUsuarioIdAndRol(usuarioId, Rol.TITULAR);
    }

    @Override
    public void eliminar(Membresia membresia) {
        jpa.delete(membresia);
    }
}
