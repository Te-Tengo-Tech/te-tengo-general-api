package tech.tetengo.api.actividad.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-33 / CA-33.1: one change of the system's configuration: who made it (a member or a PC), what
 * changed, the value before and after, and when. Append-only: the database refuses updates and
 * deletes, and the API has no endpoint to edit or delete it. PC names are kept as they were then.
 */
@Entity
@Table(name = "actividad")
public class Actividad extends EntidadDelHogar {

    static final int LARGO_OBJETO = 120;
    static final int LARGO_VALOR = 200;

    @Column(nullable = false, updatable = false, length = 80)
    private String clave;

    @Column(name = "ocurrida_en", nullable = false, updatable = false)
    private Instant ocurridaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private TipoDeActividad tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private OrigenDeActividad origen;

    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    /** The PC the change was made from, or that it is about (the app's filter by PC, CA-33.2). */
    @Column(name = "pc_id", updatable = false)
    private UUID pcId;

    @Column(name = "pc_nombre", updatable = false, length = 60)
    private String pcNombre;

    @Column(name = "camara_id", updatable = false)
    private UUID camaraId;

    /** What changed, e.g. the camera «Logitech C920» or the room «Sala». */
    @Column(updatable = false, length = LARGO_OBJETO)
    private String objeto;

    @Column(name = "valor_anterior", updatable = false, length = LARGO_VALOR)
    private String valorAnterior;

    @Column(name = "valor_nuevo", updatable = false, length = LARGO_VALOR)
    private String valorNuevo;

    protected Actividad() {}

    private Actividad(String clave, Instant ocurridaEn, TipoDeActividad tipo, OrigenDeActividad origen) {
        this.clave = Objects.requireNonNull(clave);
        this.ocurridaEn = Objects.requireNonNull(ocurridaEn);
        this.tipo = Objects.requireNonNull(tipo);
        this.origen = Objects.requireNonNull(origen);
    }

    /**
     * @param clave unique per household: a change registered twice (an event delivered again, a PC
     *     that resends its queue) is stored once
     */
    public static Actividad de(String clave, Instant ocurridaEn, TipoDeActividad tipo, OrigenDeActividad origen) {
        return new Actividad(clave, ocurridaEn, tipo, origen);
    }

    public Actividad porUsuario(UUID usuarioId) {
        this.usuarioId = usuarioId;
        return this;
    }

    public Actividad enPc(UUID pcId, String pcNombre) {
        this.pcId = pcId;
        this.pcNombre = recortar(pcNombre, 60);
        return this;
    }

    public Actividad deCamara(UUID camaraId) {
        this.camaraId = camaraId;
        return this;
    }

    public Actividad sobre(String objeto, String anterior, String nuevo) {
        this.objeto = recortar(objeto, LARGO_OBJETO);
        this.valorAnterior = recortar(anterior, LARGO_VALOR);
        this.valorNuevo = recortar(nuevo, LARGO_VALOR);
        return this;
    }

    private static String recortar(String valor, int largo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("\\p{Cntrl}", " ").strip();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > largo ? limpio.substring(0, largo) : limpio;
    }

    public String getClave() {
        return clave;
    }

    public Instant getOcurridaEn() {
        return ocurridaEn;
    }

    public TipoDeActividad getTipo() {
        return tipo;
    }

    public OrigenDeActividad getOrigen() {
        return origen;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getPcId() {
        return pcId;
    }

    public String getPcNombre() {
        return pcNombre;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public String getObjeto() {
        return objeto;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }
}
