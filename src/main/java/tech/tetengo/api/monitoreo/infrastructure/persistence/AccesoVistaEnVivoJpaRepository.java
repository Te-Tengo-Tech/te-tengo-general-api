package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

interface AccesoVistaEnVivoJpaRepository extends JpaRepository<AccesoVistaEnVivo, UUID> {

    List<AccesoVistaEnVivo> findAllByOrderByInicioDesc();

    List<AccesoVistaEnVivo> findByFinIsNull();

    List<AccesoVistaEnVivo> findByCamaraIdAndFinIsNull(UUID camaraId);

    /** Native, so not filtered by household: MediaMTX's request has no JWT. */
    @Query(value = "select id, hogar_id from accesos_vista_en_vivo where token_hash = :huella", nativeQuery = true)
    List<Object[]> porToken(String huella);

    /** Native, across households: only for the denied access log. */
    @Query(value = "select camara_id, usuario_id from accesos_vista_en_vivo where id = :id", nativeQuery = true)
    List<Object[]> deCualquierHogar(UUID id);

    /** Native, across households: only for the session end job. */
    @Query(value = "select distinct hogar_id from accesos_vista_en_vivo where fin is null", nativeQuery = true)
    List<UUID> hogaresConSesionesAbiertas();
}
