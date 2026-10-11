package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.movimientos.ConsentimientoDeMovimientosOtorgado;
import tech.tetengo.api.movimientos.ConsentimientoDeMovimientosRevocado;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-35: the older adult's consent to store their movements (body landmarks, no image), separate from
 * the monitoring consent and optional (CA-35.1). It keeps who gave it (the older adult or their
 * representative), who registered it, when, and the version of the text accepted, as the proof
 * Law 29733 asks for sensitive data (CA-35.2). Only one is in force at a time.
 */
@Entity
@Table(name = "consentimientos_de_movimientos")
public class ConsentimientoDeMovimientos extends EntidadDelHogar {

    public static final String DESDE_LA_APP = "DESDE_LA_APP";
    public static final String MONITOREO_REVOCADO = "CONSENTIMIENTO_DE_MONITOREO_REVOCADO";

    @Column(name = "otorgado_en", nullable = false, updatable = false)
    private Instant otorgadoEn;

    @Column(name = "otorgado_por", nullable = false, updatable = false, length = 120)
    private String otorgadoPor;

    /** «Su representante» rather than «Rosa, en persona». */
    @Column(nullable = false, updatable = false)
    private boolean representante;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private UUID registradoPor;

    @Column(name = "version_texto", nullable = false, updatable = false, length = 20)
    private String versionTexto;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "revocado_por")
    private UUID revocadoPor;

    @Column(name = "motivo_revocacion", length = 40)
    private String motivoRevocacion;

    @Column(name = "reemplazado_en")
    private Instant reemplazadoEn;

    protected ConsentimientoDeMovimientos() {}

    private ConsentimientoDeMovimientos(
            String otorgadoPor, boolean representante, UUID registradoPor, String versionTexto, Instant ahora) {
        this.otorgadoEn = ahora;
        this.otorgadoPor = otorgadoPor;
        this.representante = representante;
        this.registradoPor = Objects.requireNonNull(registradoPor);
        this.versionTexto = versionTexto;
    }

    /**
     * CA-35.2: registered only when the older adult accepted it, for the text version in force; from
     * then on movements may be stored.
     */
    public static ConsentimientoDeMovimientos otorgar(
            UUID hogarId,
            String otorgadoPor,
            boolean representante,
            Boolean aceptado,
            UUID registradoPor,
            String versionTexto,
            String versionVigente,
            Instant ahora) {
        if (!Boolean.TRUE.equals(aceptado)) {
            throw new ErrorDeNegocio(MovimientoError.CONSENTIMIENTO_NO_ACEPTADO);
        }
        if (versionTexto == null || !versionTexto.strip().equals(versionVigente)) {
            throw new ErrorDeNegocio(MovimientoError.VERSION_DE_TEXTO_INVALIDA);
        }
        var consentimiento = new ConsentimientoDeMovimientos(
                otorgadoPor.strip(), representante, registradoPor, versionVigente, ahora);
        consentimiento.registrarEvento(new ConsentimientoDeMovimientosOtorgado(
                hogarId, consentimiento.otorgadoPor, registradoPor, versionVigente, ahora));
        return consentimiento;
    }

    /** CA-35.4: stops storing movements; returns false if it was not in force. */
    public boolean revocar(UUID usuarioId, String motivo, Instant ahora) {
        if (!vigente()) {
            return false;
        }
        revocadoEn = ahora;
        revocadoPor = usuarioId;
        motivoRevocacion = motivo;
        registrarEvento(new ConsentimientoDeMovimientosRevocado(getHogarId(), usuarioId, motivo, ahora));
        return true;
    }

    /** A newer consent takes its place. */
    public void reemplazar(Instant ahora) {
        if (vigente()) {
            reemplazadoEn = ahora;
        }
    }

    public boolean vigente() {
        return revocadoEn == null && reemplazadoEn == null;
    }

    public Instant getOtorgadoEn() {
        return otorgadoEn;
    }

    public String getOtorgadoPor() {
        return otorgadoPor;
    }

    public boolean isRepresentante() {
        return representante;
    }

    public UUID getRegistradoPor() {
        return registradoPor;
    }

    public String getVersionTexto() {
        return versionTexto;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }

    public String getMotivoRevocacion() {
        return motivoRevocacion;
    }
}
