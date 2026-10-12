package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.monitoreo.domain.model.MosaicoDeVistaEnVivo;

interface MosaicoDeVistaEnVivoJpaRepository extends JpaRepository<MosaicoDeVistaEnVivo, UUID> {}
