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
 * CA-16.4: a push notice the push service did not accept. A job retries it until it is delivered
 * or the attempts run out; the alert is visible in the app meanwhile.
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

    protected AvisoPendiente() {}

    public AvisoPendiente(
            String tipo,
            UUID alertaId,
            UUID camaraId,
            String habitacion,
            Instant ocurridaEn,
            Collection<UUID> destinatarios,
            UUID excluido,
            Instant proximoIntento) {
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.alertaId = alertaId;
        this.camaraId = camaraId;
        this.habitacion = habitacion;
        this.ocurridaEn = Objects.requireNonNull(ocurridaEn, "ocurridaEn");
        this.destinatarios = destinatarios == null
                ? null
                : destinatarios.stream().map(UUID::toString).collect(Collectors.joining(","));
        this.excluido = excluido;
        this.intentos = 1;
        this.proximoIntento = Objects.requireNonNull(proximoIntento, "proximoIntento");
    }

    /** Another failed attempt; returns false when there are no attempts left. */
    public boolean fallo(Instant ahora, Duration espera, int intentosMaximos) {
        intentos++;
        proximoIntento = ahora.plus(espera);
        return intentos < intentosMaximos;
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
}
