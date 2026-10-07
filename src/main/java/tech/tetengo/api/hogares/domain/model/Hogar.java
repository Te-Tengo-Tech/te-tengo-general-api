package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * The household: the tenant. A global table (it is the tenant itself), so it extends
 * {@code AggregateRoot}, not {@code EntidadDelHogar}. Its owner ({@code TITULAR}) created it.
 */
@Entity
@Table(name = "hogares")
public class Hogar extends AggregateRoot {

    @Column(name = "titular_id", nullable = false, updatable = false)
    private UUID titularId;

    @Embedded
    private AdultoMayor adultoMayor;

    protected Hogar() {}

    public Hogar(UUID titularId, AdultoMayor adultoMayor) {
        this.titularId = Objects.requireNonNull(titularId, "titularId");
        this.adultoMayor = Objects.requireNonNull(adultoMayor, "adultoMayor");
    }

    public void actualizarAdultoMayor(AdultoMayor datos) {
        this.adultoMayor = Objects.requireNonNull(datos, "adultoMayor");
    }

    public UUID getTitularId() {
        return titularId;
    }

    public AdultoMayor getAdultoMayor() {
        return adultoMayor;
    }
}
