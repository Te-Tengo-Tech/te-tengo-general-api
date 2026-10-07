package tech.tetengo.api.camaras.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.Instalacion;

interface InstalacionJpaRepository extends JpaRepository<Instalacion, UUID> {

    Optional<Instalacion> findByCredencialHash(String credencialHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instalacion i where i.credencialHash = :credencialHash")
    Optional<Instalacion> findByCredencialHashForUpdate(String credencialHash);
}
