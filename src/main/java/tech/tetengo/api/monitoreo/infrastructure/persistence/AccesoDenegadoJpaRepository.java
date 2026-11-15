package tech.tetengo.api.monitoreo.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.monitoreo.domain.model.AccesoDenegado;

interface AccesoDenegadoJpaRepository extends JpaRepository<AccesoDenegado, UUID> {

    List<AccesoDenegado> findAllByOrderByOcurridoEnDescIdDesc();

    /** Native, so the row lands on the camera's household rather than on the requester's. */
    @Modifying
    @Query(
            value = "insert into accesos_denegados (id, hogar_id, ocurrido_en, canal, motivo, accion, protocolo,"
                    + " camara_id, usuario_id, hogar_del_usuario, creado_en, actualizado_en)"
                    + " values (:id, :hogarId, :ocurridoEn, :canal, :motivo, :accion, :protocolo,"
                    + " :camaraId, :usuarioId, :hogarDelUsuario, :ocurridoEn, :ocurridoEn)",
            nativeQuery = true)
    void registrar(
            UUID id,
            UUID hogarId,
            Instant ocurridoEn,
            String canal,
            String motivo,
            String accion,
            String protocolo,
            UUID camaraId,
            UUID usuarioId,
            UUID hogarDelUsuario);
}
