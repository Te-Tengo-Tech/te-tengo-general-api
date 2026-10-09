package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.monitoreo.domain.model.TransmisionEnVivo;

interface TransmisionEnVivoJpaRepository extends JpaRepository<TransmisionEnVivo, UUID> {

    Optional<TransmisionEnVivo> findByCamaraId(UUID camaraId);

    /** Native, so not filtered by household: MediaMTX's request has no JWT. */
    @Query(
            value = "select hogar_id from transmisiones_en_vivo where camara_id = :camaraId and clave_hash = :huella",
            nativeQuery = true)
    List<UUID> hogarDeClave(UUID camaraId, String huella);

    /** Transaction-scoped PostgreSQL advisory lock; released on commit or rollback. */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:clave)) bloqueo", nativeQuery = true)
    Integer bloquear(long clave);
}
