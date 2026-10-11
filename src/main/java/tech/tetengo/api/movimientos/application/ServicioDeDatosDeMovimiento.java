package tech.tetengo.api.movimientos.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.movimientos.DatosDeMovimiento;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.ConfiguracionDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.ConsentimientoDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;

@Service
public class ServicioDeDatosDeMovimiento implements DatosDeMovimiento {

    private final ConsentimientoDeMovimientosRepository consentimientos;
    private final ConfiguracionDeMovimientosRepository configuraciones;
    private final ArchivoDeMovimientoRepository archivos;
    private final BorradoDeMovimientos borrado;
    private final PropiedadesDeMovimientos propiedades;

    public ServicioDeDatosDeMovimiento(
            ConsentimientoDeMovimientosRepository consentimientos,
            ConfiguracionDeMovimientosRepository configuraciones,
            ArchivoDeMovimientoRepository archivos,
            BorradoDeMovimientos borrado,
            PropiedadesDeMovimientos propiedades) {
        this.consentimientos = consentimientos;
        this.configuraciones = configuraciones;
        this.archivos = archivos;
        this.borrado = borrado;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean consentimientoVigente() {
        return consentimientos
                .ultimo()
                .map(ConsentimientoDeMovimientos::vigente)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean guardadoEnLaNube() {
        return consentimientoVigente()
                && configuraciones
                        .actual()
                        .map(ConfiguracionDeMovimientos::getRetencionDias)
                        .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public long archivosGuardados() {
        return archivos.totales().archivos();
    }

    @Override
    @Transactional
    public long eliminarTodos() {
        return borrado.todos();
    }

    /** {@code GET /api/agente/movimientos/estado}. */
    @Transactional(readOnly = true)
    public EstadoDeMovimientos estadoParaElAgente() {
        boolean vigente = consentimientoVigente();
        Integer dias = vigente
                ? configuraciones
                        .actual()
                        .map(ConfiguracionDeMovimientos::getRetencionDias)
                        .orElse(null)
                : null;
        return new EstadoDeMovimientos(vigente, vigente, dias != null, dias, propiedades.versionTextoVigente());
    }
}
