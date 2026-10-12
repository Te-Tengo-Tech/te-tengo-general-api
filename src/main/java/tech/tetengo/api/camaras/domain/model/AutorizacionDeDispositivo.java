package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * A PC link request: the device authorization of RFC 8628. The PC holds the secret {@code device_code}
 * (only its SHA-256 is stored) and shows the {@code user_code} (also stored hashed) as a QR and as
 * text; the owner approves it in the app; the PC polls until it gets its tokens. Global table: the PC
 * has no household until the owner approves it.
 */
@Entity
@Table(name = "autorizaciones_de_dispositivo")
public class AutorizacionDeDispositivo extends AggregateRoot {

    /** RFC 8628 §3.5: {@code slow_down} adds 5 seconds to the polling interval. */
    public static final Duration AUMENTO_POR_LENTITUD = Duration.ofSeconds(5);

    /** An approved code can still be redeemed for this long, even if it expires meanwhile. */
    static final Duration CANJE_TRAS_APROBAR = Duration.ofMinutes(2);

    @Column(name = "codigo_dispositivo_hash", nullable = false, updatable = false, length = 64)
    private String codigoDispositivoHash;

    @Column(name = "codigo_usuario_hash", nullable = false, updatable = false, length = 64)
    private String codigoUsuarioHash;

    @Column(nullable = false, updatable = false, length = 60)
    private String cliente;

    @Column(name = "nombre_pc", nullable = false, updatable = false, length = 60)
    private String nombrePc;

    @Column(name = "sistema_operativo", updatable = false, length = 60)
    private String sistemaOperativo;

    @Column(name = "version_agente", updatable = false, length = 40)
    private String versionAgente;

    /** The PC's installation id, if its agent sends one (older agents do not). */
    @Column(name = "id_instalacion", updatable = false)
    private UUID idInstalacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAutorizacion estado;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "intervalo_segundos", nullable = false)
    private int intervaloSegundos;

    @Column(name = "ultima_consulta_en")
    private Instant ultimaConsultaEn;

    @Column(name = "hogar_id")
    private UUID hogarId;

    @Column(name = "pc_id")
    private UUID pcId;

    @Column(name = "resuelta_por")
    private UUID resueltaPor;

    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    @Column(name = "canjeada_en")
    private Instant canjeadaEn;

    protected AutorizacionDeDispositivo() {}

    public AutorizacionDeDispositivo(
            String codigoDispositivoHash,
            String codigoUsuarioHash,
            String cliente,
            String nombrePc,
            String sistemaOperativo,
            String versionAgente,
            UUID idInstalacion,
            Instant ahora,
            Duration vigencia,
            Duration intervalo) {
        this.codigoDispositivoHash = Objects.requireNonNull(codigoDispositivoHash);
        this.codigoUsuarioHash = Objects.requireNonNull(codigoUsuarioHash);
        this.cliente = Objects.requireNonNull(cliente);
        this.nombrePc = Pc.nombreValido(nombrePc);
        this.sistemaOperativo = Pc.recortar(sistemaOperativo, Pc.LARGO_MAXIMO_SISTEMA);
        this.versionAgente = Pc.recortar(versionAgente, Pc.LARGO_MAXIMO_VERSION);
        this.idInstalacion = idInstalacion;
        this.estado = EstadoAutorizacion.PENDIENTE;
        this.expiraEn = ahora.plus(vigencia);
        this.intervaloSegundos = (int) intervalo.toSeconds();
    }

    /** What the PC's poll gets (RFC 8628 §3.5). */
    public enum Consulta {
        PENDIENTE,
        MAS_LENTO,
        VENCIDA,
        RECHAZADA,
        LISTA,
        YA_CANJEADA
    }

    /**
     * The PC polls the token endpoint. A poll sooner than the interval since the previous one answers
     * {@code slow_down} and adds 5 s to the interval; an approved code is redeemed once ({@code LISTA}).
     */
    public Consulta consultar(Instant ahora) {
        return switch (estado) {
            case CANJEADA -> Consulta.YA_CANJEADA;
            case RECHAZADA -> Consulta.RECHAZADA;
            case APROBADA -> {
                if (ahora.isAfter(expiraEn)) {
                    yield Consulta.VENCIDA;
                }
                estado = EstadoAutorizacion.CANJEADA;
                canjeadaEn = ahora;
                yield Consulta.LISTA;
            }
            case PENDIENTE -> {
                if (!ahora.isBefore(expiraEn)) {
                    yield Consulta.VENCIDA;
                }
                Instant anterior = ultimaConsultaEn;
                ultimaConsultaEn = ahora;
                if (anterior != null && ahora.isBefore(anterior.plusSeconds(intervaloSegundos))) {
                    intervaloSegundos += (int) AUMENTO_POR_LENTITUD.toSeconds();
                    yield Consulta.MAS_LENTO;
                }
                yield Consulta.PENDIENTE;
            }
        };
    }

    /** The owner can still approve or reject it: pending and not expired (CA-28.3). */
    public boolean resoluble(Instant ahora) {
        return estado == EstadoAutorizacion.PENDIENTE && ahora.isBefore(expiraEn);
    }

    /**
     * CA-28.1: the owner approved it for their household; the PC's next poll gets tokens for that PC.
     * The user code cannot be used again (CA-28.3).
     */
    public void aprobar(UUID hogarId, UUID pcId, UUID titularId, Instant ahora) {
        exigirResoluble(ahora);
        this.estado = EstadoAutorizacion.APROBADA;
        this.hogarId = Objects.requireNonNull(hogarId);
        this.pcId = Objects.requireNonNull(pcId);
        this.resueltaPor = titularId;
        this.resueltaEn = ahora;
        Instant minimo = ahora.plus(CANJE_TRAS_APROBAR);
        if (expiraEn.isBefore(minimo)) {
            expiraEn = minimo;
        }
    }

    /** CA-28.4: «No reconozco esta PC»; the PC gets {@code access_denied}. */
    public void rechazar(UUID titularId, Instant ahora) {
        exigirResoluble(ahora);
        this.estado = EstadoAutorizacion.RECHAZADA;
        this.resueltaPor = titularId;
        this.resueltaEn = ahora;
    }

    private void exigirResoluble(Instant ahora) {
        if (!resoluble(ahora)) {
            throw new IllegalStateException("La vinculación ya no se puede resolver");
        }
    }

    public String getCliente() {
        return cliente;
    }

    public String getNombrePc() {
        return nombrePc;
    }

    public String getSistemaOperativo() {
        return sistemaOperativo;
    }

    public String getVersionAgente() {
        return versionAgente;
    }

    public UUID getIdInstalacion() {
        return idInstalacion;
    }

    public EstadoAutorizacion getEstado() {
        return estado;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public int getIntervaloSegundos() {
        return intervaloSegundos;
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public UUID getPcId() {
        return pcId;
    }
}
