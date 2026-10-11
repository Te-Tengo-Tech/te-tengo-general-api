package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.camaras.CamaraAsignada;
import tech.tetengo.api.camaras.CamaraDesconectada;
import tech.tetengo.api.camaras.CamaraReconectada;
import tech.tetengo.api.camaras.CamaraRetirada;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/** Camera installed in the household. Rules live here, not in controllers. */
@Entity
@Table(name = "camaras")
public class Camara extends EntidadDelHogar {

    static final int LARGO_MAXIMO_NOMBRE = 40;
    public static final int LARGO_MAXIMO_DISPOSITIVO = 300;
    static final int LARGO_MAXIMO_NOMBRE_DISPOSITIVO = 100;

    @Column(name = "nombre_habitacion", nullable = false, length = LARGO_MAXIMO_NOMBRE)
    private String nombreHabitacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conexion", nullable = false, length = 20)
    private EstadoConexion estadoConexion;

    @Column(name = "ultima_senal")
    private Instant ultimaSenal;

    @Column(name = "pausada_hasta")
    private Instant pausadaHasta;

    @Column(name = "deteccion_confiable", nullable = false)
    private boolean deteccionConfiable = true;

    @Column(name = "no_confiable_desde")
    private Instant noConfiableDesde;

    /** The PC the camera is connected to (US-30); null only for rows older than PCs that had none. */
    @Column(name = "pc_id")
    private UUID pcId;

    /**
     * When the camera stopped being used: its PC was unlinked (US-31). A retired camera is no longer
     * listed, its tokens stop working and its alerts keep their room name.
     */
    @Column(name = "retirada_en")
    private Instant retiradaEn;

    /** The room it watches (US-29); its name is {@code nombreHabitacion}. */
    @Column(name = "ambiente_id")
    private UUID ambienteId;

    /**
     * The operating system's unique id of the webcam (CA-29.4), so plugging another webcam or restarting
     * the PC does not change which camera watches each room. Null for installation cameras.
     */
    @Column(name = "dispositivo_id", length = LARGO_MAXIMO_DISPOSITIVO)
    private String dispositivoId;

    /** The webcam's name as the PC shows it («Webcam USB HD», CA-29.1). */
    @Column(name = "nombre_dispositivo", length = LARGO_MAXIMO_NOMBRE_DISPOSITIVO)
    private String nombreDispositivo;

    protected Camara() {}

    public Camara(String nombreHabitacion) {
        renombrar(nombreHabitacion);
        this.estadoConexion = EstadoConexion.DESCONECTADA;
    }

    /** A camera of a PC (US-29, US-30). */
    public Camara(String nombreHabitacion, UUID pcId) {
        this(nombreHabitacion);
        this.pcId = Objects.requireNonNull(pcId, "pcId");
    }

    /**
     * US-29 / CA-29.2: a webcam of a linked PC starts watching a room. It is known by the device id the
     * operating system gives it, not by its position (CA-29.4).
     */
    public static Camara deDispositivo(
            UUID hogarId,
            UUID pcId,
            String dispositivoId,
            String nombreDispositivo,
            Ambiente ambiente,
            UUID usuarioId,
            Instant ahora) {
        Camara camara = new Camara(ambiente.getNombre(), pcId);
        camara.dispositivoId = Objects.requireNonNull(dispositivoId, "dispositivoId");
        camara.nombreDispositivo = Pc.recortar(nombreDispositivo, LARGO_MAXIMO_NOMBRE_DISPOSITIVO);
        camara.ambienteId = ambiente.getId();
        camara.registrarEvento(new CamaraAsignada(
                hogarId, camara.getId(), pcId, camara.nombreDispositivo, null, ambiente.getNombre(), usuarioId, ahora));
        return camara;
    }

    /**
     * US-29: the camera watches {@code ambiente} from now on; its room name and later alerts follow
     * (CA-29.2). A retired camera of the same device is used again. Returns whether it changed.
     */
    public boolean asignarAmbiente(Ambiente ambiente, UUID usuarioId, Instant ahora) {
        boolean volvio = retiradaEn != null;
        if (!volvio
                && ambiente.getId().equals(ambienteId)
                && ambiente.getNombre().equals(nombreHabitacion)) {
            return false;
        }
        String anterior = volvio || ambienteId == null ? null : nombreHabitacion;
        retiradaEn = null;
        ambienteId = ambiente.getId();
        nombreHabitacion = ambiente.getNombre();
        registrarEvento(new CamaraAsignada(
                getHogarId(), getId(), pcId, nombreDispositivo, anterior, ambiente.getNombre(), usuarioId, ahora));
        return true;
    }

    /** The room was renamed: the camera's name follows it, with no event of its own. */
    public void seguirAmbiente(Ambiente ambiente) {
        if (ambiente.getId().equals(ambienteId)) {
            nombreHabitacion = ambiente.getNombre();
        }
    }

    /** A camera of an installation, created before rooms existed, joins the room of its name. */
    public void ubicarEn(Ambiente ambiente) {
        if (ambienteId == null) {
            ambienteId = ambiente.getId();
        }
    }

    /** The PC reports a new name for the webcam. Returns whether it changed. */
    public boolean actualizarDispositivo(String nombre) {
        String nuevo = Pc.recortar(nombre, LARGO_MAXIMO_NOMBRE_DISPOSITIVO);
        if (nuevo == null || nuevo.equals(nombreDispositivo)) {
            return false;
        }
        nombreDispositivo = nuevo;
        return true;
    }

    /** US-29: «Dejar de usar» on the PC. Returns whether it changed. */
    public boolean dejarDeUsar(Instant ahora) {
        if (!retirar(ahora)) {
            return false;
        }
        registrarEvento(new CamaraRetirada(getHogarId(), getId(), pcId, nombreDispositivo, nombreHabitacion, ahora));
        return true;
    }

    /** A camera of an installation, created before PCs existed, joins the installation's PC. */
    public void asignarPc(UUID pcId) {
        if (this.pcId == null) {
            this.pcId = Objects.requireNonNull(pcId, "pcId");
        }
    }

    /**
     * US-31: the camera's PC was unlinked. It goes offline silently: the family already knows, so no
     * {@code CAMARA_DESCONECTADA} notice is sent, and its pause ends. Returns whether it changed.
     */
    public boolean retirar(Instant ahora) {
        if (retiradaEn != null) {
            return false;
        }
        retiradaEn = ahora;
        estadoConexion = EstadoConexion.DESCONECTADA;
        pausadaHasta = null;
        return true;
    }

    public boolean retirada() {
        return retiradaEn != null;
    }

    /** CA-06.2 and CA-06.3: the name cannot be empty. */
    public void renombrar(String nombre) {
        this.nombreHabitacion = nombreDeHabitacion(nombre);
    }

    /**
     * CA-06.3: a room name of the camera endpoints, with their error codes ({@code
     * CAMARA_NOMBRE_VACIO}, {@code CAMARA_NOMBRE_MUY_LARGO}).
     */
    public static String nombreDeHabitacion(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ErrorDeNegocio(CamaraError.NOMBRE_VACIO);
        }
        if (nombre.strip().length() > LARGO_MAXIMO_NOMBRE) {
            throw new ErrorDeNegocio(CamaraError.NOMBRE_MUY_LARGO);
        }
        return nombre.strip();
    }

    /**
     * CA-07.1: the agent reports a heartbeat; the camera goes online with the time of the last signal.
     * A camera that had been online before and was disconnected is back (CA-07.3).
     */
    public void registrarSenal(Instant instante) {
        boolean reconexion = estadoConexion == EstadoConexion.DESCONECTADA && ultimaSenal != null;
        this.ultimaSenal = instante;
        this.estadoConexion = EstadoConexion.EN_LINEA;
        if (reconexion) {
            registrarEvento(new CamaraReconectada(getHogarId(), getId(), nombreHabitacion, instante));
        }
    }

    /**
     * CA-07.2: with no heartbeat since {@code limite}, the camera is disconnected. Returns whether it
     * changed, so running the job twice does nothing the second time.
     */
    public boolean desconectarSiNoHaySenalDesde(Instant limite, Instant ahora) {
        if (ultimaSenal == null || !ultimaSenal.isBefore(limite)) {
            return false;
        }
        return desconectar(ahora);
    }

    /**
     * CA-07.2: the agent reports that its webcam is not available, so the camera is disconnected right
     * away. Returns whether it changed: an already disconnected camera (or one that never came online)
     * sends no new notice.
     */
    public boolean desconectar(Instant ahora) {
        if (estadoConexion != EstadoConexion.EN_LINEA) {
            return false;
        }
        estadoConexion = EstadoConexion.DESCONECTADA;
        registrarEvento(new CamaraDesconectada(getHogarId(), getId(), nombreHabitacion, ahora));
        return true;
    }

    /**
     * CA-15.3: for over 5 minutes the agent only got frames it had to discard. {@code desde} is kept
     * from the first report, so a repeated one neither moves it nor notifies again.
     */
    public boolean marcarDeteccionNoConfiable(Instant desde) {
        if (!deteccionConfiable) {
            return false;
        }
        deteccionConfiable = false;
        noConfiableDesde = Objects.requireNonNull(desde, "desde");
        return true;
    }

    public void marcarDeteccionConfiable() {
        deteccionConfiable = true;
        noConfiableDesde = null;
    }

    /** US-22 / CA-22.1: capture and detection stop until {@code hasta}; a new pause replaces the old one. */
    public void pausar(Instant hasta) {
        this.pausadaHasta = Objects.requireNonNull(hasta, "hasta");
    }

    /** Resume now (manual resume). */
    public void reanudar() {
        this.pausadaHasta = null;
    }

    /** CA-22.3: the pause is over; returns whether it ended now. */
    public boolean finalizarPausaSiVencio(Instant ahora) {
        if (pausadaHasta == null || ahora.isBefore(pausadaHasta)) {
            return false;
        }
        pausadaHasta = null;
        return true;
    }

    /** CA-22.1: a paused camera neither captures nor detects until its pause ends. */
    public boolean estaPausada(Instant ahora) {
        return pausadaHasta != null && ahora.isBefore(pausadaHasta);
    }

    public UUID getPcId() {
        return pcId;
    }

    public Instant getRetiradaEn() {
        return retiradaEn;
    }

    public UUID getAmbienteId() {
        return ambienteId;
    }

    public String getDispositivoId() {
        return dispositivoId;
    }

    public String getNombreDispositivo() {
        return nombreDispositivo;
    }

    public String getNombreHabitacion() {
        return nombreHabitacion;
    }

    public EstadoConexion getEstadoConexion() {
        return estadoConexion;
    }

    public Instant getUltimaSenal() {
        return ultimaSenal;
    }

    public Instant getPausadaHasta() {
        return pausadaHasta;
    }

    public boolean isDeteccionConfiable() {
        return deteccionConfiable;
    }

    /** Null while detection is reliable. */
    public Instant getNoConfiableDesde() {
        return noConfiableDesde;
    }

    /**
     * The camera is created by the agent's first registration, which the project team runs when it
     * installs the camera (CA-06.1).
     */
    public Instant getInstaladaEn() {
        return getCreadoEn();
    }
}
