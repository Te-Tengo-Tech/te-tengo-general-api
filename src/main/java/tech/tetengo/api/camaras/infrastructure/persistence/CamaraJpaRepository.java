package tech.tetengo.api.camaras.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Internal to infrastructure: use cases depend on the {@code CamaraRepository} port. */
interface CamaraJpaRepository extends JpaRepository<Camara, UUID> {

    /** Native, so not filtered by household: only for jobs, which then work household by household. */
    @Query(
            value = "select id, hogar_id from camaras where estado_conexion = 'EN_LINEA' and ultima_senal < :limite",
            nativeQuery = true)
    List<Object[]> enLineaSinSenalDesde(Instant limite);

    /** Native, so not filtered by household: only for the resume job. */
    @Query(value = "select id, hogar_id from camaras where pausada_hasta <= :ahora", nativeQuery = true)
    List<Object[]> conPausaVencida(Instant ahora);
}
