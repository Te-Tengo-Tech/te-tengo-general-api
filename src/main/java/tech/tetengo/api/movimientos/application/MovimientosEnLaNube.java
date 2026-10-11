package tech.tetengo.api.movimientos.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.ConfiguracionDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.ConsentimientoDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-37: the owner chooses how long movements are kept in the cloud: off (the default) or 7, 30, 90 or
 * 365 days (CA-37.1), only with the consent to store them in force. A shorter period deletes the older
 * files at the next daily run (CA-37.4); turning it off deletes them all then.
 */
@Service
public class MovimientosEnLaNube {

    private final ConfiguracionDeMovimientosRepository configuraciones;
    private final ConsentimientoDeMovimientosRepository consentimientos;
    private final ArchivoDeMovimientoRepository archivos;
    private final Clock reloj;

    public MovimientosEnLaNube(
            ConfiguracionDeMovimientosRepository configuraciones,
            ConsentimientoDeMovimientosRepository consentimientos,
            ArchivoDeMovimientoRepository archivos,
            Clock reloj) {
        this.configuraciones = configuraciones;
        this.consentimientos = consentimientos;
        this.archivos = archivos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public MovimientosEnLaNubeConsultados consultar() {
        var totales = archivos.totales();
        return new MovimientosEnLaNubeConsultados(
                configuraciones
                        .actual()
                        .map(ConfiguracionDeMovimientos::getRetencionDias)
                        .orElse(null),
                consentimientoVigente(),
                archivos.primero().orElse(null),
                totales.archivos(),
                totales.tamanoBytes());
    }

    @Transactional
    public MovimientosEnLaNubeConsultados cambiar(Integer dias, UUID titularId) {
        if (dias != null && !consentimientoVigente()) {
            throw new ErrorDeNegocio(MovimientoError.SIN_CONSENTIMIENTO_DE_MOVIMIENTOS);
        }
        ConfiguracionDeMovimientos configuracion = configuraciones.actual().orElseGet(ConfiguracionDeMovimientos::new);
        if (configuracion.cambiarRetencion(dias, titularId, reloj.instant())) {
            configuraciones.guardar(configuracion);
        }
        return consultar();
    }

    private boolean consentimientoVigente() {
        return consentimientos
                .ultimo()
                .map(ConsentimientoDeMovimientos::vigente)
                .orElse(false);
    }
}
