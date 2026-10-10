package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * CA-16.4: a push notice on its way (outbox). It is saved with the change that calls for it, so a
 * notice is never lost when the API stops, and sent once that transaction commits. If it is not
 * delivered, a job retries it until it is delivered or its deadline ({@code venceEn}) passes; the
 * alert is visible in the app meanwhile.
 */
@Entity
@Table(name = "avisos_pendientes")
public class AvisoPendiente extends EntidadDelHogar {

    @Column(nullable = false, updatable = false, length = 40)
    private String tipo;

    @Column(name = "alerta_id", updatable = false)
    private UUID alertaId;

    @Column(name = "camara_id", updatable = false)
    private UUID camaraId;

    @Column(updatable = false, length = 40)
    private String habitacion;

    @Column(name = "ocurrida_en", nullable = false, updatable = false)
    private Instant ocurridaEn;

    /** Comma-separated user ids; null means every member of the household. */
    @Column(updatable = false)
    private String destinatarios;

    @Column(updatable = false)
    private UUID excluido;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "proximo_intento", nullable = false)
    private Instant proximoIntento;

    /** No attempt after this. */
    @Column(name = "vence_en", nullable = false, updatable = false)
    private Instant venceEn;

    protected AvisoPendiente() {}

    public AvisoPendiente(
            String tipo,
            UUID alertaId,
            UUID camaraId,
            String habitacion,
            Instant ocurridaEn,
            Collection<UUID> destinatarios,
            UUID excluido,
            Instant proximoIntento,
            Instant venceEn) {
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.alertaId = alertaId;
        this.camaraId = camaraId;
        this.habitacion = habitacion;
        this.ocurridaEn = Objects.requireNonNull(ocurridaEn, "ocurridaEn");
        this.destinatarios = destinatarios == null
                ? null
                : destinatarios.stream().map(UUID::toString).collect(Collectors.joining(","));
        this.excluido = excluido;
        this.intentos = 0;
        this.proximoIntento = Objects.requireNonNull(proximoIntento, "proximoIntento");
        this.venceEn = Objects.requireNonNull(venceEn, "venceEn");
    }

    /**
     * Whether an attempt may start now: the first one (right after the commit) or a due retry. An
     * attempt in progress has pushed {@code proximoIntento} ahead, so the retry job and the first
     * attempt never send it twice at once.
     */
    public boolean puedeIntentarse(Instant ahora) {
        return intentos == 0 || !proximoIntento.isAfter(ahora);
    }

    /** An attempt starts: nobody else picks the notice up during {@code reserva}. */
    public void iniciarIntento(Instant ahora, Duration reserva) {
        intentos++;
        proximoIntento = ahora.plus(reserva);
    }

    /**
     * The attempt did not deliver it: the next one is {@code espera} later. Returns false when that is
     * past the deadline, so the notice is given up.
     */
    public boolean fallo(Instant ahora, Duration espera) {
        proximoIntento = ahora.plus(espera);
        return !proximoIntento.isAfter(venceEn);
    }

    /** Due now: a phone of the family registered again and can receive it. */
    public void adelantar(Instant ahora) {
        if (proximoIntento.isAfter(ahora)) {
            proximoIntento = ahora;
        }
    }

    public String getTipo() {
        return tipo;
    }

    public UUID getAlertaId() {
        return alertaId;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public String getHabitacion() {
        return habitacion;
    }

    public Instant getOcurridaEn() {
        return ocurridaEn;
    }

    /** Null means every member of the household. */
    public List<UUID> getDestinatarios() {
        return destinatarios == null
                ? null
                : Arrays.stream(destinatarios.split(",")).map(UUID::fromString).toList();
    }

    public UUID getExcluido() {
        return excluido;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getProximoIntento() {
        return proximoIntento;
    }

    public Instant getVenceEn() {
        return venceEn;
    }
}
