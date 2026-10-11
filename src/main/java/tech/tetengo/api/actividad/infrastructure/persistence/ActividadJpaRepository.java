package tech.tetengo.api.actividad.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tech.tetengo.api.actividad.domain.model.Actividad;

interface ActividadJpaRepository extends JpaRepository<Actividad, UUID>, JpaSpecificationExecutor<Actividad> {

    boolean existsByClave(String clave);
}
