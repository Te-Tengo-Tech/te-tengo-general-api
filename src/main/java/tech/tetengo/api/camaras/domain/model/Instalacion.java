package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * An installed household agent with its webcam (AGENT_CONTRACT.md). Created by the project team with
 * an installation credential; the first registration creates its camera (CA-06.1). Global table,
 * because the credential is what tells which household the agent belongs to.
 */
@Entity
@Table(name = "instalaciones")
public class Instalacion extends AggregateRoot {

    @Column(name = "hogar_id", nullable = false, updatable = false)
    private UUID hogarId;

    @Column(name = "credencial_hash", nullable = false, updatable = false, length = 64)
    private String credencialHash;

    @Column(name = "camara_id")
    private UUID camaraId;

    protected Instalacion() {}

    public Instalacion(UUID hogarId, String credencialHash) {
        this.hogarId = Objects.requireNonNull(hogarId, "hogarId");
        this.credencialHash = Objects.requireNonNull(credencialHash, "credencialHash");
    }

    public void asignarCamara(UUID camaraId) {
        this.camaraId = Objects.requireNonNull(camaraId, "camaraId");
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public UUID getCamaraId() {
        return camaraId;
    }
}
