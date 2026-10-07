package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

interface AccesoVistaEnVivoJpaRepository extends JpaRepository<AccesoVistaEnVivo, UUID> {

    List<AccesoVistaEnVivo> findAllByOrderByInicioDesc();

    /** Native, so not filtered by household: the stream connection has no JWT. */
    @Query(value = "select id, hogar_id from accesos_vista_en_vivo where token_hash = :huella", nativeQuery = true)
    List<Object[]> porToken(String huella);
}
