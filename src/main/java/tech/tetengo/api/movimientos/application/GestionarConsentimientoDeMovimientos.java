package tech.tetengo.api.movimientos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.hogares.ConsentimientoDelHogar;
import tech.tetengo.api.hogares.ConsentimientoRevocado;
import tech.tetengo.api.movimientos.application.ConsentimientoDeMovimientosConsultado.Eliminacion;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.ConfiguracionDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.ConsentimientoDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.EliminacionDeMovimientosRepository;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;
import tech.tetengo.api.movimientos.domain.model.EliminacionDeMovimientos;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * US-35: the separate, optional consent to store movements. Any member reads it; the owner registers
 * it (CA-35.2, only with the monitoring consent in force, CA-35.1) and revokes it (CA-35.4: nothing more
 * is stored, the cloud movements are deleted and the family is told when it is done; the monitoring
 * consent and the alerts stay). Revoking the monitoring consent ends it too; those movements are deleted
 * with the recordings (CA-09.1).
 */
@Service
public class GestionarConsentimientoDeMovimientos {

    private final ConsentimientoDeMovimientosRepository consentimientos;
    private final ConfiguracionDeMovimientosRepository configuraciones;
    private final EliminacionDeMovimientosRepository eliminaciones;
    private final ArchivoDeMovimientoRepository archivos;
    private final ConsentimientoDelHogar monitoreo;
    private final DirectorioDeUsuarios usuarios;
    private final PropiedadesDeMovimientos propiedades;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public GestionarConsentimientoDeMovimientos(
            ConsentimientoDeMovimientosRepository consentimientos,
            ConfiguracionDeMovimientosRepository configuraciones,
            EliminacionDeMovimientosRepository eliminaciones,
            ArchivoDeMovimientoRepository archivos,
            ConsentimientoDelHogar monitoreo,
            DirectorioDeUsuarios usuarios,
            PropiedadesDeMovimientos propiedades,
            EjecutorEnHogar enHogar,
            Clock reloj) {
        this.consentimientos = consentimientos;
        this.configuraciones = configuraciones;
        this.eliminaciones = eliminaciones;
        this.archivos = archivos;
        this.monitoreo = monitoreo;
        this.usuarios = usuarios;
        this.propiedades = propiedades;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ConsentimientoDeMovimientosConsultado consultar() {
        var ultimo = consentimientos.ultimo().orElse(null);
        Eliminacion eliminacion = eliminaciones
                .ultima()
                .map(e -> e.getTerminadaEn() == null
                        ? new Eliminacion("PROGRAMADA", archivos.totales().archivos(), e.getProgramadaEn(), null)
                        : new Eliminacion(
                                "TERMINADA",
                                e.getArchivosEliminados() == null ? 0 : e.getArchivosEliminados(),
                                e.getProgramadaEn(),
                                e.getTerminadaEn()))
                .orElse(null);
        return new ConsentimientoDeMovimientosConsultado(
                ultimo != null && ultimo.vigente(),
                propiedades.versionTextoVigente(),
                ultimo,
                ultimo == null ? null : ultimo.getRegistradoPor(),
                ultimo == null
                        ? null
                        : usuarios.buscar(ultimo.getRegistradoPor())
                                .map(DirectorioDeUsuarios.Usuario::nombre)
                                .orElse(null),
                eliminacion);
    }

    /** CA-35.2: registered with who gave it, who registered it, when and the text version. */
    @Transactional
    public ConsentimientoDeMovimientosConsultado otorgar(
            UUID titularId, String otorgadoPor, boolean representante, Boolean aceptado, String versionTexto) {
        if (!monitoreo.vigente()) {
            throw new ErrorDeNegocio(MovimientoError.SIN_CONSENTIMIENTO);
        }
        Instant ahora = reloj.instant();
        var nuevo = ConsentimientoDeMovimientos.otorgar(
                HogarActual.obtener().orElseThrow(),
                otorgadoPor,
                representante,
                aceptado,
                titularId,
                versionTexto,
                propiedades.versionTextoVigente(),
                ahora);
        consentimientos.ultimo().ifPresent(anterior -> {
            anterior.reemplazar(ahora);
            consentimientos.guardar(anterior);
        });
        consentimientos.guardar(nuevo);
        return consultar();
    }

    /** CA-35.4: returns how many cloud files will be deleted. */
    @Transactional
    public long revocar(UUID titularId) {
        ConsentimientoDeMovimientos vigente = consentimientos
                .ultimo()
                .filter(ConsentimientoDeMovimientos::vigente)
                .orElseThrow(() -> new ErrorDeNegocio(MovimientoError.CONSENTIMIENTO_DE_MOVIMIENTOS_NO_ENCONTRADO));
        Instant ahora = reloj.instant();
        long guardados = archivos.totales().archivos();
        vigente.revocar(titularId, ConsentimientoDeMovimientos.DESDE_LA_APP, ahora);
        consentimientos.guardar(vigente);
        apagarNube(ahora);
        eliminaciones.guardar(new EliminacionDeMovimientos(ahora));
        return guardados;
    }

    /**
     * CA-09.1: without the monitoring consent nothing is captured, so the movement consent ends too.
     * Its movements are deleted with the recordings, by the deletion of {@code alertas}.
     */
    @Async
    @TransactionalEventListener
    public void alRevocarseElMonitoreo(ConsentimientoRevocado evento) {
        enHogar.ejecutar(evento.hogarId(), () -> {
            consentimientos
                    .ultimo()
                    .filter(ConsentimientoDeMovimientos::vigente)
                    .ifPresent(vigente -> {
                        vigente.revocar(null, ConsentimientoDeMovimientos.MONITOREO_REVOCADO, evento.revocadoEn());
                        consentimientos.guardar(vigente);
                    });
            apagarNube(evento.revocadoEn());
        });
    }

    private void apagarNube(Instant ahora) {
        configuraciones.actual().ifPresent(configuracion -> {
            configuracion.apagar(ahora);
            configuraciones.guardar(configuracion);
        });
    }
}
