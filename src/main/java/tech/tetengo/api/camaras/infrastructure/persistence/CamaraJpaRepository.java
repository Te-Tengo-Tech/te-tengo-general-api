package tech.tetengo.api.camaras.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Internal to infrastructure: use cases depend on the {@code CamaraRepository} port. */
interface CamaraJpaRepository extends JpaRepository<Camara, UUID> {

    /** Native, so not filtered by household: only for jobs, which then work household by household. */
    @Query(
            value =
                    "select id, hogar_id from camaras where estado_conexion = 'EN_LINEA' and ultima_senal < :limite and retirada_en is null",
            nativeQuery = true)
    List<Object[]> enLineaSinSenalDesde(Instant limite);

    /** Native, so not filtered by household: only for the resume job. */
    @Query(
            value = "select id, hogar_id from camaras where pausada_hasta <= :ahora and retirada_en is null",
            nativeQuery = true)
    List<Object[]> conPausaVencida(Instant ahora);

    Optional<Camara> findByIdAndRetiradaEnIsNull(UUID id);

    List<Camara> findByRetiradaEnIsNull(Sort orden);

    List<Camara> findByPcIdAndRetiradaEnIsNull(UUID pcId, Sort orden);

    /** Native, so not filtered by household: the agent token is checked before its household is bound. */
    @Query(
            value = "select c.pc_id, c.retirada_en is not null, coalesce(p.estado = 'VINCULADA', true)"
                    + " from camaras c left join pcs p on p.id = c.pc_id"
                    + " where c.id = :camaraId and c.hogar_id = :hogarId",
            nativeQuery = true)
    List<Object[]> deAgente(UUID hogarId, UUID camaraId);
}
