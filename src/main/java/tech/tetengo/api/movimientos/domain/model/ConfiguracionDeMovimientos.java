package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tech.tetengo.api.movimientos.RetencionDeMovimientosCambiada;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-37: how long the household's movements are kept in the cloud. Off until the owner chooses 7, 30,
 * 90 or 365 days (CA-37.1); at most a year, for proportionality (change request 2.4). One row per
 * household.
 */
@Entity
@Table(name = "configuraciones_de_movimientos")
public class ConfiguracionDeMovimientos extends EntidadDelHogar {

    public static final Set<Integer> PLAZOS = Set.of(7, 30, 90, 365);

    /** Null: movements are not stored in the cloud. */
    @Column(name = "retencion_dias")
    private Integer retencionDias;

    @Column(name = "cambiada_en")
    private Instant cambiadaEn;

    @Column(name = "cambiada_por")
    private UUID cambiadaPor;

    public ConfiguracionDeMovimientos() {}

    /** CA-37.1: a period of the list, or null to stop storing in the cloud. Returns whether it changed. */
    public boolean cambiarRetencion(Integer dias, UUID usuarioId, Instant ahora) {
        return cambiarRetencion(dias, usuarioId, getHogarId(), ahora);
    }

    /** The same for a configuration not saved yet, whose household is not filled in. */
    public boolean cambiarRetencion(Integer dias, UUID usuarioId, UUID hogarId, Instant ahora) {
        if (dias != null && !PLAZOS.contains(dias)) {
            throw new ErrorDeNegocio(MovimientoError.RETENCION_INVALIDA);
        }
        if (Objects.equals(dias, retencionDias)) {
            return false;
        }
        Integer anterior = retencionDias;
        retencionDias = dias;
        cambiadaEn = ahora;
        cambiadaPor = usuarioId;
        registrarEvento(new RetencionDeMovimientosCambiada(hogarId, anterior, dias, usuarioId, ahora));
        return true;
    }

    /** CA-35.4: without the consent nothing is kept, and the owner must choose again after a new one. */
    public void apagar(Instant ahora) {
        if (retencionDias != null) {
            retencionDias = null;
            cambiadaEn = ahora;
            cambiadaPor = null;
        }
    }

    public Optional<Duration> retencion() {
        return Optional.ofNullable(retencionDias).map(Duration::ofDays);
    }

    public Integer getRetencionDias() {
        return retencionDias;
    }

    public Instant getCambiadaEn() {
        return cambiadaEn;
    }
}
