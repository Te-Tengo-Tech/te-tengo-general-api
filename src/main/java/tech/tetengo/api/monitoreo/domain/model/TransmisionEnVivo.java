package tech.tetengo.api.monitoreo.domain.model;

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
 * A camera's live transmission, which exists while at least one live view session of the camera is
 * open. The household agent publishes it to MediaMTX with a publish token issued here (only its hash is
 * stored), drawing the current {@link ModoDeVista}.
 */
@Entity
@Table(name = "transmisiones_en_vivo")
public class TransmisionEnVivo extends EntidadDelHogar {

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Column(name = "clave_hash", nullable = false, length = 64)
    private String claveHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModoDeVista modo;

    @Column(name = "iniciada_en", nullable = false, updatable = false)
    private Instant iniciadaEn;

    protected TransmisionEnVivo() {}

    public TransmisionEnVivo(UUID camaraId, String claveHash, ModoDeVista modo, Instant iniciadaEn) {
        this.camaraId = Objects.requireNonNull(camaraId, "camaraId");
        this.claveHash = Objects.requireNonNull(claveHash, "claveHash");
        this.modo = Objects.requireNonNull(modo, "modo");
        this.iniciadaEn = Objects.requireNonNull(iniciadaEn, "iniciadaEn");
    }

    /** A new publish token, e.g. when the agent connects again; the previous one stops working. */
    public void renovarClave(String claveHash) {
        this.claveHash = Objects.requireNonNull(claveHash, "claveHash");
    }

    /** True if the mode changed. */
    public boolean cambiarModo(ModoDeVista nuevo) {
        if (nuevo == null || nuevo == modo) {
            return false;
        }
        modo = nuevo;
        return true;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public String getClaveHash() {
        return claveHash;
    }

    public ModoDeVista getModo() {
        return modo;
    }

    public Instant getIniciadaEn() {
        return iniciadaEn;
    }
}
