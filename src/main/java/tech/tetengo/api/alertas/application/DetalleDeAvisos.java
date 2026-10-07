package tech.tetengo.api.alertas.application;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.TipoAlerta;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.hogares.AdultoMayorDelHogar;
import tech.tetengo.api.hogares.OrdenDeAviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.TipoDeAlerta;
import tech.tetengo.api.shared.application.port.TipoAviso;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * Reads what the prototype's notice text needs (docs/NOTIFICATIONS.md) for the household in context,
 * right before each attempt, so queued notices need nothing more stored: the older adult's first
 * name, the alert's type and start, the escalation wait and who attended the alert. A value that
 * cannot be read is left out and never stops the notice.
 */
@Component
class DetalleDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(DetalleDeAvisos.class);

    private static final Set<TipoAviso> CON_NOMBRE = EnumSet.of(
            TipoAviso.ALERTA_CAIDA,
            TipoAviso.ALERTA_MOVIMIENTO_INESTABLE,
            TipoAviso.ALERTA_ACTUALIZADA_A_CAIDA,
            TipoAviso.CAIDA_CONFIRMADA,
            TipoAviso.SE_LEVANTO,
            TipoAviso.DETECCION_NO_CONFIABLE);

    private static final Set<TipoAviso> CON_ALERTA = EnumSet.of(
            TipoAviso.ALERTA_ACTUALIZADA_A_CAIDA,
            TipoAviso.ALERTA_ATENDIDA,
            TipoAviso.ALERTA_ESCALADA,
            TipoAviso.SIN_CONTACTO_SECUNDARIO);

    private static final Set<TipoAviso> CON_ESPERA =
            EnumSet.of(TipoAviso.ALERTA_ESCALADA, TipoAviso.SIN_CONTACTO_SECUNDARIO);

    private final AdultoMayorDelHogar adultoMayor;
    private final AlertaRepository alertas;
    private final DirectorioDeUsuarios usuarios;
    private final OrdenDeAviso ordenDeAviso;

    DetalleDeAvisos(
            AdultoMayorDelHogar adultoMayor,
            AlertaRepository alertas,
            DirectorioDeUsuarios usuarios,
            OrdenDeAviso ordenDeAviso) {
        this.adultoMayor = adultoMayor;
        this.alertas = alertas;
        this.usuarios = usuarios;
        this.ordenDeAviso = ordenDeAviso;
    }

    Aviso completar(Aviso aviso) {
        try {
            return aviso.conDetalle(detalle(aviso));
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el detalle del push {}; se envía sin él", aviso.tipo(), e);
            return aviso;
        }
    }

    private Detalle detalle(Aviso aviso) {
        TipoAviso tipo = aviso.tipo();
        String nombre = CON_NOMBRE.contains(tipo) ? adultoMayor.nombreDePila().orElse(null) : null;
        Optional<Alerta> alerta = CON_ALERTA.contains(tipo) && aviso.alertaId() != null
                ? alertas.buscar(aviso.alertaId())
                : Optional.empty();
        TipoDeAlerta tipoDeAlerta = alerta.map(
                        a -> a.getTipo() == TipoAlerta.CAIDA ? TipoDeAlerta.CAIDA : TipoDeAlerta.MOVIMIENTO_INESTABLE)
                .orElse(null);
        // The alert keeps the time of the unstable movement it began as (CA-17.3).
        Instant desde = tipo == TipoAviso.ALERTA_ACTUALIZADA_A_CAIDA
                ? alerta.map(Alerta::getOcurridaEn).orElse(null)
                : null;
        String quien = tipo == TipoAviso.ALERTA_ATENDIDA
                ? alerta.map(Alerta::getAtendidaPor)
                        .flatMap(usuarios::buscar)
                        .map(u -> primeraPalabra(u.nombre()))
                        .orElse(null)
                : null;
        Integer espera = CON_ESPERA.contains(tipo)
                ? HogarActual.obtener()
                        .map(hogar -> ordenDeAviso.de(hogar).esperaMinutos())
                        .orElse(null)
                : null;
        return new Detalle(nombre, tipoDeAlerta, desde, espera, quien);
    }

    /** «Carmen» for «Carmen Huamán», as the prototype names who attended. */
    private static String primeraPalabra(String nombre) {
        return nombre == null || nombre.isBlank() ? null : nombre.strip().split("\\s+", 2)[0];
    }
}
