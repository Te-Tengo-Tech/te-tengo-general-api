package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
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

    @Column(name = "aviso_principal_id")
    private UUID avisoPrincipalId;

    @Column(name = "aviso_secundario_id")
    private UUID avisoSecundarioId;

    @Column(name = "aviso_espera_minutos")
    private Integer avisoEsperaMinutos;

    /** CA-10.2: the owner can wait 3, 5 or 10 minutes before the secondary contact is told. */
    public static final Set<Integer> ESPERAS_PERMITIDAS = Set.of(3, 5, 10);

    /** CA-10.3. */
    public static final int ESPERA_POR_DEFECTO = 5;

    protected Hogar() {}

    public Hogar(UUID titularId, AdultoMayor adultoMayor) {
        this.titularId = Objects.requireNonNull(titularId, "titularId");
        this.adultoMayor = Objects.requireNonNull(adultoMayor, "adultoMayor");
    }

    public void actualizarAdultoMayor(AdultoMayor datos) {
        this.adultoMayor = Objects.requireNonNull(datos, "adultoMayor");
    }

    /**
     * US-10: the owner sets the contact order (CA-10.1) and the wait before escalating: 3, 5 or 10
     * minutes (CA-10.2). Whether the contacts are members is checked by the caller.
     */
    public void configurarAviso(UUID principalId, UUID secundarioId, int esperaMinutos) {
        if (!ESPERAS_PERMITIDAS.contains(esperaMinutos)) {
            throw new ErrorDeNegocio(HogarError.ESPERA_INVALIDA);
        }
        if (principalId == null || principalId.equals(secundarioId)) {
            throw new ErrorDeNegocio(HogarError.CONTACTO_NO_ES_FAMILIAR);
        }
        this.avisoPrincipalId = principalId;
        this.avisoSecundarioId = secundarioId;
        this.avisoEsperaMinutos = esperaMinutos;
    }

    /** CA-10.3: 5 minutes until the owner chooses. */
    public int getEsperaMinutos() {
        return avisoEsperaMinutos == null ? ESPERA_POR_DEFECTO : avisoEsperaMinutos;
    }

    public UUID getAvisoPrincipalId() {
        return avisoPrincipalId;
    }

    public UUID getAvisoSecundarioId() {
        return avisoSecundarioId;
    }

    public UUID getTitularId() {
        return titularId;
    }

    public AdultoMayor getAdultoMayor() {
        return adultoMayor;
    }
}
