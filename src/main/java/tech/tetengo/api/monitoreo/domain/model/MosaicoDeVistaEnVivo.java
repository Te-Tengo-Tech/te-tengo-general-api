package tech.tetengo.api.monitoreo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-41: one opening of «Ver todas», the live view of several cameras at once. The live view sessions
 * opened from it carry its id, so the access log shows it as one entry with the rooms seen (CA-41.6).
 * Only the member who opened it may add sessions to it.
 */
@Entity
@Table(name = "mosaicos_vista_en_vivo")
public class MosaicoDeVistaEnVivo extends EntidadDelHogar {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false, updatable = false)
    private Instant inicio;

    protected MosaicoDeVistaEnVivo() {}

    public MosaicoDeVistaEnVivo(UUID usuarioId, Instant inicio) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.inicio = Objects.requireNonNull(inicio, "inicio");
    }

    /** The mosaic was opened by this member. */
    public boolean esDe(UUID usuario) {
        return usuarioId.equals(usuario);
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getInicio() {
        return inicio;
    }
}
