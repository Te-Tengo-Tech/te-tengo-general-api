package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.hogares.ConsentimientoOtorgado;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-05: the older adult's consent to camera monitoring. It is only registered when the older adult
 * accepts it, including that linked family members can watch live at any time (CA-05.4), and it keeps
 * the date and time it was granted (CA-05.3).
 */
@Entity
@Table(name = "consentimientos")
public class Consentimiento extends EntidadDelHogar {

    @Column(name = "otorgado_en", nullable = false, updatable = false)
    private Instant otorgadoEn;

    @Column(name = "otorgado_por", nullable = false, updatable = false, length = 120)
    private String otorgadoPor;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private UUID registradoPor;

    @Column(name = "aceptado_por_adulto_mayor", nullable = false, updatable = false)
    private boolean aceptadoPorAdultoMayor;

    @Column(name = "vista_en_vivo_aceptada", nullable = false, updatable = false)
    private boolean vistaEnVivoAceptada;

    @Column(name = "reemplazado_en")
    private Instant reemplazadoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    protected Consentimiento() {}

    private Consentimiento(String otorgadoPor, UUID registradoPor, Instant ahora) {
        this.otorgadoEn = ahora;
        this.otorgadoPor = otorgadoPor.strip();
        this.registradoPor = Objects.requireNonNull(registradoPor, "registradoPor");
        this.aceptadoPorAdultoMayor = true;
        this.vistaEnVivoAceptada = true;
    }

    /** CA-05.4: both flags must be true; otherwise nothing is registered. */
    public static Consentimiento otorgar(
            UUID hogarId,
            String otorgadoPor,
            UUID registradoPor,
            Boolean aceptadoPorAdultoMayor,
            Boolean vistaEnVivoAceptada,
            Instant ahora) {
        if (!Boolean.TRUE.equals(aceptadoPorAdultoMayor) || !Boolean.TRUE.equals(vistaEnVivoAceptada)) {
            throw new ErrorDeNegocio(HogarError.CONSENTIMIENTO_NO_ACEPTADO);
        }
        Consentimiento consentimiento = new Consentimiento(otorgadoPor, registradoPor, ahora);
        consentimiento.registrarEvento(new ConsentimientoOtorgado(hogarId, ahora));
        return consentimiento;
    }

    /** A newer consent takes its place. */
    public void reemplazar(Instant ahora) {
        if (vigente()) {
            reemplazadoEn = ahora;
        }
    }

    public boolean vigente() {
        return reemplazadoEn == null && revocadoEn == null;
    }

    public Instant getOtorgadoEn() {
        return otorgadoEn;
    }

    public String getOtorgadoPor() {
        return otorgadoPor;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }

    public boolean isAceptadoPorAdultoMayor() {
        return aceptadoPorAdultoMayor;
    }

    public boolean isVistaEnVivoAceptada() {
        return vistaEnVivoAceptada;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }
}
