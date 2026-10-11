package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.camaras.PcDesvinculada;
import tech.tetengo.api.camaras.PcRenombrada;
import tech.tetengo.api.camaras.PcVinculada;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * A PC of the household running Te Tengo Captura (US-28, US-30). Each PC has its own cameras; a
 * household may have several PCs. It is linked by the owner from the app (device grant) or comes from
 * an installation of the project team, and stays linked until it is unlinked (US-31) or goes 90 days
 * without connecting (CA-28.7).
 */
@Entity
@Table(name = "pcs")
public class Pc extends EntidadDelHogar {

    static final int LARGO_MAXIMO_NOMBRE = 60;
    static final int LARGO_MAXIMO_SISTEMA = 60;
    static final int LARGO_MAXIMO_VERSION = 40;

    /** Name of the PC of installations, which report none (the brief's «PC de la casa»). */
    public static final String NOMBRE_DE_INSTALACION = "PC de la casa";

    @Column(nullable = false, length = LARGO_MAXIMO_NOMBRE)
    private String nombre;

    @Column(name = "sistema_operativo", length = LARGO_MAXIMO_SISTEMA)
    private String sistemaOperativo;

    @Column(name = "version_agente", length = LARGO_MAXIMO_VERSION)
    private String versionAgente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private OrigenPc origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPc estado;

    @Column(name = "vinculada_en", nullable = false, updatable = false)
    private Instant vinculadaEn;

    /** The owner who approved the link; null for installations. */
    @Column(name = "vinculada_por", updatable = false)
    private UUID vinculadaPor;

    /** Last PC heartbeat or token renewal; the cameras' heartbeats count too (see the use cases). */
    @Column(name = "ultima_senal")
    private Instant ultimaSenal;

    /** Last time the PC got tokens: 90 days without it and the link expires (CA-28.7). */
    @Column(name = "ultimo_uso_en")
    private Instant ultimoUsoEn;

    @Column(name = "desvinculada_en")
    private Instant desvinculadaEn;

    @Column(name = "desvinculada_por")
    private UUID desvinculadaPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_desvinculacion", length = 30)
    private MotivoDesvinculacion motivoDesvinculacion;

    protected Pc() {}

    private Pc(
            String nombre,
            String sistemaOperativo,
            String versionAgente,
            OrigenPc origen,
            UUID vinculadaPor,
            Instant ahora) {
        this.nombre = nombreValido(nombre);
        this.sistemaOperativo = recortar(sistemaOperativo, LARGO_MAXIMO_SISTEMA);
        this.versionAgente = recortar(versionAgente, LARGO_MAXIMO_VERSION);
        this.origen = origen;
        this.estado = EstadoPc.VINCULADA;
        this.vinculadaEn = Objects.requireNonNull(ahora, "ahora");
        this.vinculadaPor = vinculadaPor;
        this.ultimoUsoEn = ahora;
    }

    /** US-28 / CA-28.1: the owner approved the PC's code in the app. */
    public static Pc vincular(
            UUID hogarId, String nombre, String sistemaOperativo, String versionAgente, UUID titularId, Instant ahora) {
        Pc pc = new Pc(
                nombre,
                sistemaOperativo,
                versionAgente,
                OrigenPc.VINCULACION,
                Objects.requireNonNull(titularId),
                ahora);
        pc.registrarEvento(new PcVinculada(hogarId, pc.getId(), pc.nombre, titularId, ahora));
        return pc;
    }

    /** The PC of an installation credential (CA-06.1): no owner approved it. */
    public static Pc deInstalacion(Instant ahora) {
        return new Pc(NOMBRE_DE_INSTALACION, null, null, OrigenPc.INSTALACION, null, ahora);
    }

    /** The owner renames the PC in the app; returns the previous name, or null if nothing changed. */
    public String renombrar(String nuevo, UUID usuarioId, Instant ahora) {
        String valido = nombreValido(nuevo);
        if (valido.equals(nombre)) {
            return null;
        }
        String anterior = nombre;
        nombre = valido;
        registrarEvento(new PcRenombrada(getHogarId(), getId(), anterior, valido, usuarioId, ahora));
        return anterior;
    }

    /**
     * US-31: the PC leaves the household. Its tokens stop working at once and its cameras stop being
     * used (the use case retires them). Returns false if it was already unlinked.
     *
     * @param usuarioId who unlinked it from the app; null when it was the PC or the system
     * @param habitaciones rooms that lose detection, for the activity log and the notice
     */
    public boolean desvincular(
            MotivoDesvinculacion motivo, UUID usuarioId, List<UUID> camaras, List<String> habitaciones, Instant ahora) {
        if (estado == EstadoPc.DESVINCULADA) {
            return false;
        }
        estado = EstadoPc.DESVINCULADA;
        desvinculadaEn = ahora;
        desvinculadaPor = usuarioId;
        motivoDesvinculacion = Objects.requireNonNull(motivo, "motivo");
        registrarEvento(new PcDesvinculada(
                getHogarId(),
                getId(),
                nombre,
                motivo.name(),
                usuarioId,
                List.copyOf(camaras),
                List.copyOf(habitaciones),
                ahora));
        return true;
    }

    /** The PC got tokens (link or renewal): it is in use and connected. */
    public void registrarUso(Instant ahora) {
        ultimoUsoEn = ahora;
        registrarSenal(ahora);
    }

    /** The PC (or one of its cameras) reported a heartbeat; the version and OS are updated if sent. */
    public void registrarSenal(Instant ahora) {
        if (ultimaSenal == null || ahora.isAfter(ultimaSenal)) {
            ultimaSenal = ahora;
        }
    }

    /** Returns whether the reported version or system changed. */
    public boolean actualizarDatos(String version, String sistema) {
        boolean cambio = false;
        String nuevaVersion = recortar(version, LARGO_MAXIMO_VERSION);
        if (nuevaVersion != null && !nuevaVersion.equals(versionAgente)) {
            versionAgente = nuevaVersion;
            cambio = true;
        }
        String nuevoSistema = recortar(sistema, LARGO_MAXIMO_SISTEMA);
        if (nuevoSistema != null && !nuevoSistema.equals(sistemaOperativo)) {
            sistemaOperativo = nuevoSistema;
            cambio = true;
        }
        return cambio;
    }

    public boolean vinculada() {
        return estado == EstadoPc.VINCULADA;
    }

    /** CA-28.7: 90 days or more without getting tokens; installations never expire. */
    public boolean vencida(Instant limite) {
        return vinculada() && origen == OrigenPc.VINCULACION && ultimoUsoEn != null && !ultimoUsoEn.isAfter(limite);
    }

    static String nombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ErrorDeNegocio(CamaraError.PC_NOMBRE_VACIO);
        }
        String limpio = sinControles(nombre).strip();
        if (limpio.isEmpty()) {
            throw new ErrorDeNegocio(CamaraError.PC_NOMBRE_VACIO);
        }
        if (limpio.length() > LARGO_MAXIMO_NOMBRE) {
            throw new ErrorDeNegocio(CamaraError.PC_NOMBRE_MUY_LARGO);
        }
        return limpio;
    }

    /** Free text reported by the agent: one line, cut to the column. */
    static String recortar(String valor, int largo) {
        if (valor == null) {
            return null;
        }
        String limpio = sinControles(valor).strip();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > largo ? limpio.substring(0, largo) : limpio;
    }

    private static String sinControles(String valor) {
        return valor.replaceAll("\\p{Cntrl}", " ");
    }

    public String getNombre() {
        return nombre;
    }

    public String getSistemaOperativo() {
        return sistemaOperativo;
    }

    public String getVersionAgente() {
        return versionAgente;
    }

    public OrigenPc getOrigen() {
        return origen;
    }

    public EstadoPc getEstado() {
        return estado;
    }

    public Instant getVinculadaEn() {
        return vinculadaEn;
    }

    public UUID getVinculadaPor() {
        return vinculadaPor;
    }

    public Instant getUltimaSenal() {
        return ultimaSenal;
    }

    public Instant getUltimoUsoEn() {
        return ultimoUsoEn;
    }

    public Instant getDesvinculadaEn() {
        return desvinculadaEn;
    }

    public MotivoDesvinculacion getMotivoDesvinculacion() {
        return motivoDesvinculacion;
    }
}
