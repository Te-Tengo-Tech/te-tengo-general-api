package tech.tetengo.api.movimientos.application;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.ConfiguracionDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.ConsentimientoDeMovimientosRepository;
import tech.tetengo.api.movimientos.application.port.ResumenDeMovimientoRepository;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.movimientos.domain.model.ArchivoDeMovimiento;
import tech.tetengo.api.movimientos.domain.model.ConfiguracionDeMovimientos;
import tech.tetengo.api.movimientos.domain.model.ConsentimientoDeMovimientos;
import tech.tetengo.api.movimientos.domain.model.ResumenDiarioDeMovimiento;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.ZonaHoraria;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * US-37 / CA-37.2: every 5 minutes of capture the PC uploads a camera's movement file straight to object
 * storage with a pre-signed URL (the bytes never reach the API), then confirms it, and the index gets
 * one row per file with a daily summary. Only with the consent in force and a cloud retention chosen;
 * a file already outside the retention is refused. A file still pending can be asked for again
 * (CA-37.3: the PC retries what it could not upload).
 */
@Service
public class SubidaDeMovimientos {

    private final ArchivoDeMovimientoRepository archivos;
    private final ResumenDeMovimientoRepository resumenes;
    private final ConsentimientoDeMovimientosRepository consentimientos;
    private final ConfiguracionDeMovimientosRepository configuraciones;
    private final AlmacenamientoDeMovimientos almacenamiento;
    private final CamarasDelHogar camaras;
    private final PropiedadesDeMovimientos propiedades;
    private final Clock reloj;

    public SubidaDeMovimientos(
            ArchivoDeMovimientoRepository archivos,
            ResumenDeMovimientoRepository resumenes,
            ConsentimientoDeMovimientosRepository consentimientos,
            ConfiguracionDeMovimientosRepository configuraciones,
            AlmacenamientoDeMovimientos almacenamiento,
            CamarasDelHogar camaras,
            PropiedadesDeMovimientos propiedades,
            Clock reloj) {
        this.archivos = archivos;
        this.resumenes = resumenes;
        this.consentimientos = consentimientos;
        this.configuraciones = configuraciones;
        this.almacenamiento = almacenamiento;
        this.camaras = camaras;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public record Subida(UUID archivoId, URI url, Map<String, String> cabeceras, Instant expiraEn) {}

    /** The bounds of the file were validated by the controller (order, length, size, checksum). */
    @Transactional
    public Subida preparar(
            UUID camaraId, Instant desde, Instant hasta, long tamanoBytes, String sha256, int fotogramas) {
        camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(MovimientoError.CAMARA_NO_ENCONTRADA));
        Duration retencion = retencionVigente();
        Instant ahora = reloj.instant();
        if (hasta.isBefore(ahora.minus(retencion))) {
            throw new ErrorDeNegocio(MovimientoError.FUERA_DEL_PLAZO);
        }
        ArchivoDeMovimiento archivo = archivos.deCamaraDesde(camaraId, desde).orElse(null);
        if (archivo != null && archivo.disponible()) {
            throw new ErrorDeNegocio(MovimientoError.ARCHIVO_YA_SUBIDO, Map.of("archivoId", archivo.getId()));
        }
        String huella = sha256.toLowerCase(java.util.Locale.ROOT);
        if (archivo == null) {
            archivo = new ArchivoDeMovimiento(
                    HogarActual.obtener().orElseThrow(), camaraId, desde, hasta, tamanoBytes, huella, fotogramas);
        } else {
            archivo.actualizar(hasta, tamanoBytes, huella, fotogramas);
        }
        archivo = archivos.guardar(archivo);
        Instant expira = ahora.plus(propiedades.vigenciaUrlSubida());
        var subida = almacenamiento.urlDeSubida(archivo.getClave(), PropiedadesDeMovimientos.CONTENT_TYPE, expira);
        return new Subida(archivo.getId(), subida.url(), subida.cabeceras(), expira);
    }

    /**
     * The PC finished the PUT: the file enters the index if the storage has it. Without the consent the
     * object is deleted instead (it was revoked while the file was uploading).
     */
    @Transactional(noRollbackFor = ErrorDeNegocio.class)
    public ArchivoDeMovimiento confirmar(UUID camaraId, UUID archivoId) {
        ArchivoDeMovimiento archivo = archivos.buscar(archivoId)
                .filter(a -> a.getCamaraId().equals(camaraId))
                .orElseThrow(() -> new ErrorDeNegocio(MovimientoError.ARCHIVO_NO_ENCONTRADO));
        if (archivo.disponible()) {
            return archivo;
        }
        if (!consentimientoVigente()) {
            almacenamiento.eliminar(java.util.List.of(archivo.getClave()));
            archivos.eliminar(java.util.List.of(archivo));
            throw new ErrorDeNegocio(MovimientoError.SIN_CONSENTIMIENTO_DE_MOVIMIENTOS);
        }
        long tamano = almacenamiento
                .tamano(archivo.getClave())
                .orElseThrow(() -> new ErrorDeNegocio(MovimientoError.ARCHIVO_NO_SUBIDO));
        Instant ahora = reloj.instant();
        archivo.confirmar(tamano, ahora);
        LocalDate dia = LocalDate.ofInstant(archivo.getDesde(), ZonaHoraria.DEL_HOGAR);
        ResumenDiarioDeMovimiento resumen =
                resumenes.deCamaraYDia(camaraId, dia).orElseGet(() -> new ResumenDiarioDeMovimiento(camaraId, dia));
        resumen.sumar(archivo);
        resumenes.guardar(resumen);
        return archivos.guardar(archivo);
    }

    private Duration retencionVigente() {
        if (!consentimientoVigente()) {
            throw new ErrorDeNegocio(MovimientoError.SIN_CONSENTIMIENTO_DE_MOVIMIENTOS);
        }
        return configuraciones
                .actual()
                .flatMap(ConfiguracionDeMovimientos::retencion)
                .orElseThrow(() -> new ErrorDeNegocio(MovimientoError.NUBE_DESACTIVADA));
    }

    private boolean consentimientoVigente() {
        return consentimientos
                .ultimo()
                .map(ConsentimientoDeMovimientos::vigente)
                .orElse(false);
    }
}
