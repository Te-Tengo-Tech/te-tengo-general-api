package tech.tetengo.api.alertas.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.alertas.domain.model.EliminacionDeGrabaciones;

interface EliminacionDeGrabacionesJpaRepository extends JpaRepository<EliminacionDeGrabaciones, UUID> {

    List<EliminacionDeGrabaciones> findByCompletadaEnIsNullOrderBySolicitadaEnAsc();

    /** Native, so not filtered by household: only for the deletion job. */
    @Query(
            value = "select distinct hogar_id from eliminaciones_de_grabaciones where completada_en is null",
            nativeQuery = true)
    List<UUID> hogaresConPendientes();
}
