package tech.tetengo.api.camaras.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc;

interface TokenDeRenovacionJpaRepository extends JpaRepository<TokenDeRenovacionDePc, UUID> {

    Optional<TokenDeRenovacionDePc> findByTokenHash(String huella);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenDeRenovacionDePc t where t.tokenHash = :huella")
    Optional<TokenDeRenovacionDePc> findByTokenHashForUpdate(String huella);

    @Modifying
    @Query("update TokenDeRenovacionDePc t set t.revocadoEn = :ahora, t.actualizadoEn = :ahora"
            + " where t.pcId = :pcId and t.revocadoEn is null")
    int revocarTodosDe(UUID pcId, Instant ahora);
}
