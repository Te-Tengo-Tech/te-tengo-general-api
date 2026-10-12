package tech.tetengo.api.movimientos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.movimientos.application.RepeticionAbierta.Archivo;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.RepeticionRepository;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.movimientos.domain.model.ArchivoDeMovimiento;
import tech.tetengo.api.movimientos.domain.model.RepeticionDeMovimiento;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-39: any member replays what happened in a camera as an animated skeleton, never video (CA-39.1).
 * The app gets the index entries of the moment with short-lived URLs and draws them; only within the
 * retention and with the consent in force (CA-39.4). Every replay is logged with who watched, the
 * camera and moment, when it started and how long it lasted (CA-39.3).
 */
@Service
public class Repeticiones {

    private final RepeticionRepository repeticiones;
    private final ArchivoDeMovimientoRepository archivos;
    private final AlmacenamientoDeMovimientos almacenamiento;
    private final CamarasDelHogar camaras;
    private final ServicioDeDatosDeMovimiento datos;
    private final DirectorioDeUsuarios usuarios;
    private final PropiedadesDeMovimientos propiedades;
    private final Clock reloj;

    public Repeticiones(
            RepeticionRepository repeticiones,
            ArchivoDeMovimientoRepository archivos,
            AlmacenamientoDeMovimientos almacenamiento,
            CamarasDelHogar camaras,
            ServicioDeDatosDeMovimiento datos,
            DirectorioDeUsuarios usuarios,
            PropiedadesDeMovimientos propiedades,
            Clock reloj) {
        this.repeticiones = repeticiones;
        this.archivos = archivos;
        this.almacenamiento = almacenamiento;
        this.camaras = camaras;
        this.datos = datos;
        this.usuarios = usuarios;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** CA-39.1: opens a replay of the camera for {@code [desde, hasta)}; the range was validated. */
    @Transactional
    public RepeticionAbierta abrir(UUID usuarioId, UUID camaraId, Instant desde, Instant hasta) {
        String habitacion = camaras.buscar(camaraId)
                .map(CamarasDelHogar.CamaraDelHogar::nombreHabitacion)
                .orElseThrow(() -> new ErrorDeNegocio(MovimientoError.CAMARA_NO_ENCONTRADA));
        exigirConsentimiento();
        RepeticionDeMovimiento repeticion = repeticiones.guardar(new RepeticionDeMovimiento(
                camaraId, usuarioId, desde, hasta, reloj.instant(), propiedades.duracionMaximaRepeticion()));
        return abierta(repeticion, habitacion, desde, hasta);
    }

    /** The member moves along the timeline of an open replay: more files, the same log entry. */
    @Transactional
    public RepeticionAbierta archivos(UUID usuarioId, UUID repeticionId, Instant desde, Instant hasta) {
        RepeticionDeMovimiento repeticion = abiertaDe(usuarioId, repeticionId);
        exigirConsentimiento();
        repeticion.ampliar(desde, hasta);
        repeticiones.guardar(repeticion);
        String habitacion = camaras.buscar(repeticion.getCamaraId())
                .map(CamarasDelHogar.CamaraDelHogar::nombreHabitacion)
                .orElse(null);
        return abierta(repeticion, habitacion, desde, hasta);
    }

    /** CA-39.3: the member closed it; closing it twice changes nothing. */
    @Transactional
    public void cerrar(UUID usuarioId, UUID repeticionId) {
        repeticiones
                .buscar(repeticionId)
                .filter(r -> r.getUsuarioId().equals(usuarioId))
                .filter(r -> r.finalizar(reloj.instant()))
                .ifPresent(repeticiones::guardar);
    }

    /** CA-39.3: the replays of the household, newest first. */
    @Transactional(readOnly = true)
    public List<AccesoARepeticion> accesos() {
        Instant ahora = reloj.instant();
        List<RepeticionDeMovimiento> todas = repeticiones.recientesPrimero();
        Map<UUID, DirectorioDeUsuarios.Usuario> nombres = usuarios.buscarTodos(todas.stream()
                .map(RepeticionDeMovimiento::getUsuarioId)
                .distinct()
                .toList());
        return todas.stream()
                .map(r -> new AccesoARepeticion(
                        r.getId(),
                        r.getUsuarioId(),
                        nombres.containsKey(r.getUsuarioId())
                                ? nombres.get(r.getUsuarioId()).nombre()
                                : null,
                        r.getCamaraId(),
                        camaras.estado(r.getCamaraId())
                                .map(CamarasDelHogar.EstadoDeCamara::nombreHabitacion)
                                .orElse(null),
                        r.getMomentoDesde(),
                        r.getMomentoHasta(),
                        r.getInicio(),
                        r.duracionSegundos(ahora)))
                .toList();
    }

    private RepeticionAbierta abierta(
            RepeticionDeMovimiento repeticion, String habitacion, Instant desde, Instant hasta) {
        Instant expira = reloj.instant().plus(propiedades.vigenciaUrlLectura());
        List<Archivo> encontrados = archivos.disponiblesEntre(repeticion.getCamaraId(), desde, hasta).stream()
                .map(a -> archivo(a, expira))
                .toList();
        return new RepeticionAbierta(
                repeticion.getId(),
                repeticion.getCamaraId(),
                habitacion,
                desde,
                hasta,
                archivos.primero().orElse(null),
                datos.guardadoEnLaNube(),
                encontrados,
                expira);
    }

    private Archivo archivo(ArchivoDeMovimiento archivo, Instant expira) {
        return new Archivo(
                archivo.getId(),
                archivo.getDesde(),
                archivo.getHasta(),
                archivo.getTamanoBytes(),
                archivo.getFotogramas(),
                archivo.getSha256(),
                almacenamiento.urlDeLectura(archivo.getClave(), expira),
                archivo.ligeroDisponible()
                        ? new RepeticionAbierta.Ligero(
                                almacenamiento.urlDeLectura(archivo.claveLigera(), expira),
                                archivo.getLigeroTamanoBytes(),
                                archivo.getLigeroSha256())
                        : null);
    }

    private RepeticionDeMovimiento abiertaDe(UUID usuarioId, UUID repeticionId) {
        return repeticiones
                .buscar(repeticionId)
                .filter(r -> r.abiertaPara(usuarioId, reloj.instant()))
                .orElseThrow(() -> new ErrorDeNegocio(MovimientoError.REPETICION_NO_ENCONTRADA));
    }

    /** CA-39.4: without the consent there is nothing to replay. */
    private void exigirConsentimiento() {
        if (!datos.consentimientoVigente()) {
            throw new ErrorDeNegocio(MovimientoError.SIN_CONSENTIMIENTO_DE_MOVIMIENTOS);
        }
    }
}
