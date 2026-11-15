package tech.tetengo.api.monitoreo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * A recorded denial on a camera of the household (ADR 0015), as the household reads it. Rows are written
 * with a native insert under the camera's household, never through this entity: the refused request may
 * come from another household, or from none.
 */
@Entity
@Table(name = "accesos_denegados")
public class AccesoDenegado extends EntidadDelHogar {

    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private Instant ocurridoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private CanalDeAcceso canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private MotivoDeDenegacion motivo;

    @Column(updatable = false, length = 20)
    private String accion;

    @Column(updatable = false, length = 20)
    private String protocolo;

    @Column(name = "camara_id", updatable = false)
    private UUID camaraId;

    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    @Column(name = "hogar_del_usuario", updatable = false)
    private UUID hogarDelUsuario;

    protected AccesoDenegado() {}

    /** Made by a member of this household, from this household; otherwise who asked is not revealed. */
    public boolean deUnMiembro() {
        return usuarioId != null && getHogarId() != null && getHogarId().equals(hogarDelUsuario);
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public CanalDeAcceso getCanal() {
        return canal;
    }

    public MotivoDeDenegacion getMotivo() {
        return motivo;
    }

    public String getAccion() {
        return accion;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getHogarDelUsuario() {
        return hogarDelUsuario;
    }
}
