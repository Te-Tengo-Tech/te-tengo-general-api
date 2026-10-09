package tech.tetengo.api.alertas.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.alertas.domain.model.AvisoPendiente;

interface AvisoPendienteJpaRepository extends JpaRepository<AvisoPendiente, UUID> {

    List<AvisoPendiente> findByProximoIntentoLessThanEqualOrderByCreadoEnAsc(Instant ahora);

    /** Native, so not filtered by household: only for the retry job. */
    @Query(
            value = "select distinct hogar_id from avisos_pendientes where proximo_intento <= :ahora",
            nativeQuery = true)
    List<UUID> hogaresConVencidos(Instant ahora);
}
