package tech.tetengo.api.alertas.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoAlerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;

interface AlertaJpaRepository extends JpaRepository<Alerta, UUID>, JpaSpecificationExecutor<Alerta> {

    List<Alerta> findByClipClaveIsNotNullAndClipEliminadoEnIsNull();

    List<Alerta> findByEstadoAndEscaladaEnIsNull(EstadoAlerta estado);

    /** Native, so not filtered by household: only for the escalation job. */
    @Query(
            value = "select distinct hogar_id from alertas"
                    + " where estado = 'ACTIVA' and escalada_en is null and ocurrida_en <= :limite",
            nativeQuery = true)
    List<UUID> hogaresConActivasSinEscalarAntesDe(Instant limite);

    Optional<Alerta> findFirstByCamaraIdAndTipoAndEstadoOrderByOcurridaEnDesc(
            UUID camaraId, TipoAlerta tipo, EstadoAlerta estado);

    Optional<Alerta> findFirstByCamaraIdAndTipoAndEstadoAndConfirmadaFalseAndRecuperadaEnIsNullOrderByOcurridaEnDesc(
            UUID camaraId, TipoAlerta tipo, EstadoAlerta estado);

    Optional<Alerta> findFirstByCamaraIdAndTipoAndRecuperadaEnIsNullOrderByOcurridaEnDesc(
            UUID camaraId, TipoAlerta tipo);
}
