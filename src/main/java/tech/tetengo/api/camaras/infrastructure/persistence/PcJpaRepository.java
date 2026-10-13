package tech.tetengo.api.camaras.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.EstadoPc;
import tech.tetengo.api.camaras.domain.model.Pc;

interface PcJpaRepository extends JpaRepository<Pc, UUID> {

    Optional<Pc> findByIdAndEstado(UUID id, EstadoPc estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pc p where p.id = :id and p.estado = :estado")
    Optional<Pc> findByIdAndEstadoForUpdate(UUID id, EstadoPc estado);

    List<Pc> findByEstadoOrderByVinculadaEnAsc(EstadoPc estado);

    /** Native, so not filtered by household: the agent token is checked before its household is bound. */
    @Query(
            value = "select count(*) > 0 from pcs where id = :pcId and hogar_id = :hogarId and estado = 'VINCULADA'",
            nativeQuery = true)
    boolean vinculada(UUID hogarId, UUID pcId);

    /** Native, across households: whether a link code belongs to an already linked PC (US-28). */
    @Query(
            value = "select id, hogar_id from pcs where estado = 'VINCULADA' and id_instalacion = :idInstalacion"
                    + " order by vinculada_en limit 1",
            nativeQuery = true)
    List<Object[]> vinculadaConInstalacion(UUID idInstalacion);

    /** Native, across households: only for the expiry job. */
    @Query(
            value = "select id, hogar_id from pcs where estado = 'VINCULADA' and origen = 'VINCULACION'"
                    + " and ultimo_uso_en <= :limite",
            nativeQuery = true)
    List<Object[]> sinUsoDesde(Instant limite);
}
