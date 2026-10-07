package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.alertas.domain.model.Dispositivo;

interface DispositivoJpaRepository extends JpaRepository<Dispositivo, UUID> {

    Optional<Dispositivo> findByTokenPush(String tokenPush);

    List<Dispositivo> findByUsuarioIdIn(Collection<UUID> usuarioIds);
}
