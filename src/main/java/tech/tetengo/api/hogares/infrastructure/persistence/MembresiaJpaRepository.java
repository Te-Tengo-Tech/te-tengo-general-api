package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.model.Rol;

interface MembresiaJpaRepository extends JpaRepository<Membresia, UUID> {

    Optional<Membresia> findByHogarIdAndUsuarioId(UUID hogarId, UUID usuarioId);

    List<Membresia> findByUsuarioIdOrderByCreadoEnAsc(UUID usuarioId);

    List<Membresia> findByHogarIdOrderByCreadoEnAsc(UUID hogarId);

    boolean existsByUsuarioIdAndRol(UUID usuarioId, Rol rol);
}
