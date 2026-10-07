package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoAlerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;

interface AlertaJpaRepository extends JpaRepository<Alerta, UUID> {

    Optional<Alerta> findFirstByCamaraIdAndTipoAndEstadoOrderByOcurridaEnDesc(
            UUID camaraId, TipoAlerta tipo, EstadoAlerta estado);

    Optional<Alerta> findFirstByCamaraIdAndTipoAndEstadoAndConfirmadaFalseAndRecuperadaEnIsNullOrderByOcurridaEnDesc(
            UUID camaraId, TipoAlerta tipo, EstadoAlerta estado);

    Optional<Alerta> findFirstByCamaraIdAndTipoAndRecuperadaEnIsNullOrderByOcurridaEnDesc(
            UUID camaraId, TipoAlerta tipo);
}
