package tech.tetengo.api.hogares.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tech.tetengo.api.hogares.domain.model.Hogar;

interface HogarJpaRepository extends JpaRepository<Hogar, UUID> {}
