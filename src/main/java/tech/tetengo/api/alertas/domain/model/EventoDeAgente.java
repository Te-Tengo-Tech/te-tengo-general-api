package tech.tetengo.api.alertas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/** An event received from the household agent; {@code eventoId} makes delivery idempotent. */
@Entity
@Table(name = "eventos_de_agente")
public class EventoDeAgente extends EntidadDelHogar {

    @Column(name = "evento_id", nullable = false, updatable = false)
    private UUID eventoId;

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoEvento tipo;

    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private Instant ocurridoEn;

    /** Kinematic parameters of the classifier, as sent (JSON). */
    @Column(updatable = false)
    private String parametros;

    @Column(name = "alerta_id")
    private UUID alertaId;

    protected EventoDeAgente() {}

    public EventoDeAgente(UUID eventoId, UUID camaraId, TipoEvento tipo, Instant ocurridoEn, String parametros) {
        this.eventoId = Objects.requireNonNull(eventoId, "eventoId");
        this.camaraId = Objects.requireNonNull(camaraId, "camaraId");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.ocurridoEn = Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        this.parametros = parametros;
    }

    public void asociarAlerta(UUID alertaId) {
        this.alertaId = alertaId;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public UUID getAlertaId() {
        return alertaId;
    }
}
