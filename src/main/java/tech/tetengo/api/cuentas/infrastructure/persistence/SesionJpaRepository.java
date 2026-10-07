package tech.tetengo.api.cuentas.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.cuentas.domain.model.SesionDeUsuario;

interface SesionJpaRepository extends JpaRepository<SesionDeUsuario, UUID> {

    Optional<SesionDeUsuario> findByTokenRefrescoHash(String tokenRefrescoHash);

    @Modifying
    @Query("update SesionDeUsuario s set s.cerradaEn = :ahora where s.usuarioId = :usuarioId and s.cerradaEn is null")
    void cerrarTodas(UUID usuarioId, Instant ahora);
}
