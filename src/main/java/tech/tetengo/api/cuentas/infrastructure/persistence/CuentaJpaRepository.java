package tech.tetengo.api.cuentas.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.cuentas.domain.model.Cuenta;

/** Internal to infrastructure: use cases depend on the {@code CuentaRepository} port. */
interface CuentaJpaRepository extends JpaRepository<Cuenta, UUID> {

    Optional<Cuenta> findByCorreo(String correo);

    boolean existsByCorreo(String correo);
}
