package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.movimientos.domain.model.EliminacionDeMovimientos;

interface EliminacionDeMovimientosJpaRepository extends JpaRepository<EliminacionDeMovimientos, UUID> {

    Optional<EliminacionDeMovimientos> findFirstByOrderByProgramadaEnDesc();

    List<EliminacionDeMovimientos> findByTerminadaEnIsNull();

    /** Native, across households: only for the job. */
    @Query(
            value = "select distinct hogar_id from eliminaciones_de_movimientos where terminada_en is null",
            nativeQuery = true)
    List<UUID> hogaresConPendientes();
}
