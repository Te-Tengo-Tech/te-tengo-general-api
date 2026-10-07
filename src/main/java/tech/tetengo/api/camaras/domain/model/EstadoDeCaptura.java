package tech.tetengo.api.camaras.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * Capture state of the household, kept from the consent events of {@code hogares}: the household
 * agent does not process video without a current consent (CA-05.1, CA-05.2).
 */
@Entity
@Table(name = "estados_de_captura")
public class EstadoDeCaptura extends EntidadDelHogar {

    @Column(name = "consentimiento_vigente", nullable = false)
    private boolean consentimientoVigente;

    public EstadoDeCaptura() {}

    public void otorgarConsentimiento() {
        consentimientoVigente = true;
    }

    public void revocarConsentimiento() {
        consentimientoVigente = false;
    }

    public boolean isConsentimientoVigente() {
        return consentimientoVigente;
    }
}
