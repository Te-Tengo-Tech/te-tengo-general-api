package tech.tetengo.api.cuentas.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

@Repository
class CuentaRepositoryAdapter implements CuentaRepository {

    private final CuentaJpaRepository jpa;

    CuentaRepositoryAdapter(CuentaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Cuenta guardar(Cuenta cuenta) {
        try {
            return jpa.saveAndFlush(cuenta);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous registrations with the same e-mail: the unique index decides.
            throw new ErrorDeNegocio(CuentaError.CORREO_EN_USO);
        }
    }

    @Override
    public Optional<Cuenta> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Cuenta> buscarTodos(Collection<UUID> ids) {
        return jpa.findAllById(ids);
    }

    @Override
    public Optional<Cuenta> buscarPorCorreo(String correoNormalizado) {
        return jpa.findByCorreo(correoNormalizado);
    }

    @Override
    public boolean existeCorreo(String correoNormalizado) {
        return jpa.existsByCorreo(correoNormalizado);
    }
}
