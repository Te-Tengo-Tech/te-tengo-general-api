package tech.tetengo.api.monitoreo.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.CambiarModo;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Detener;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Mensaje;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.ParaCamara;
import tech.tetengo.api.monitoreo.application.port.CanalDelAgente.Transmitir;
import tech.tetengo.api.monitoreo.application.port.ServicioDeTransmision;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.ModoDeVista;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.monitoreo.domain.model.TransmisionEnVivo;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * A camera's transmission follows its open sessions (API contract §4): it starts with the first one
 * ({@code transmitir:true} with a new publish token), changes mode while it runs, and stops with the last
 * one ({@code transmitir:false}). A pause or a revoked consent stops it at once and kicks the publisher
 * and the readers out of MediaMTX. Callers hold a transaction of the camera's household; messages and
 * MediaMTX calls go out after it commits.
 */
@Service
public class Transmisiones {

    private final TransmisionEnVivoRepository transmisiones;
    private final AccesoVistaEnVivoRepository accesos;
    private final CanalDelAgente agentes;
    private final ServicioDeTransmision servicio;
    private final PropiedadesDeVistaEnVivo propiedades;
    private final CamarasDelHogar camaras;

    public Transmisiones(
            TransmisionEnVivoRepository transmisiones,
            AccesoVistaEnVivoRepository accesos,
            CanalDelAgente agentes,
            ServicioDeTransmision servicio,
            PropiedadesDeVistaEnVivo propiedades,
            CamarasDelHogar camaras) {
        this.transmisiones = transmisiones;
        this.accesos = accesos;
        this.agentes = agentes;
        this.servicio = servicio;
        this.propiedades = propiedades;
        this.camaras = camaras;
    }

    /**
     * Sends a live view message of the camera after the commit: on the camera's own channel, or, for a
     * webcam not in use (a preview, US-29), on its PC's channel with the camera and device ids. The route
     * is resolved now, inside the caller's transaction.
     */
    private void enviarTrasConfirmar(UUID camaraId, Mensaje mensaje) {
        Runnable envio = ruta(camaraId, mensaje);
        TrasConfirmar.ejecutar(envio);
    }

    private Runnable ruta(UUID camaraId, Mensaje mensaje) {
        return camaras.buscarDetectada(camaraId)
                .filter(camara -> !camara.enUso())
                .<Runnable>map(camara ->
                        () -> agentes.enviar(camara.pcId(), new ParaCamara(camaraId, camara.dispositivoId(), mensaje)))
                .orElse(() -> agentes.enviar(camaraId, mensaje));
    }

    /** Takes the camera's lock until the transaction ends (see {@link TransmisionEnVivoRepository#bloquear}). */
    public void bloquear(UUID camaraId) {
        transmisiones.bloquear(camaraId);
    }

    /**
     * The camera streams: starts its transmission if it is not running, or switches it to {@code modo}.
     * Returns the stream's mode. {@code modo} null keeps the current one ({@code VIDEO} for a new one).
     */
    public ModoDeVista asegurar(UUID camaraId, ModoDeVista modo, Instant ahora) {
        Optional<TransmisionEnVivo> actual = transmisiones.deCamara(camaraId);
        if (actual.isEmpty()) {
            String clave = Secretos.generar();
            TransmisionEnVivo nueva = transmisiones.guardar(new TransmisionEnVivo(
                    camaraId, Secretos.huella(clave), modo != null ? modo : ModoDeVista.PREDETERMINADO, ahora));
            transmitir(camaraId, clave, nueva.getModo());
            return nueva.getModo();
        }
        TransmisionEnVivo transmision = actual.get();
        if (transmision.cambiarModo(modo)) {
            transmisiones.guardar(transmision);
            ModoDeVista nuevo = transmision.getModo();
            enviarTrasConfirmar(camaraId, new CambiarModo(nuevo));
        }
        return transmision.getModo();
    }

    /**
     * The agent (re)connected: if the camera has open sessions it gets the transmission again, with a new
     * publish token; otherwise a leftover transmission is dropped.
     */
    public void alConectarseElAgente(UUID camaraId, Instant ahora) {
        bloquear(camaraId);
        boolean conSesiones = accesos.abiertasDe(camaraId).stream().anyMatch(s -> s.admiteLectura(ahora));
        Optional<TransmisionEnVivo> actual = transmisiones.deCamara(camaraId);
        if (!conSesiones) {
            actual.ifPresent(transmisiones::eliminar);
            return;
        }
        if (actual.isEmpty()) {
            asegurar(camaraId, null, ahora);
            return;
        }
        String clave = Secretos.generar();
        TransmisionEnVivo transmision = actual.get();
        transmision.renovarClave(Secretos.huella(clave));
        transmisiones.guardar(transmision);
        transmitir(camaraId, clave, transmision.getModo());
    }

    /** Ends the sessions at the given instant and kicks their viewers out of MediaMTX. */
    public void finalizar(Collection<AccesoVistaEnVivo> sesiones, Instant fin) {
        List<String> huellas = sesiones.stream()
                .filter(sesion -> sesion.finalizar(fin))
                .map(sesion -> accesos.guardar(sesion).getTokenHash())
                .toList();
        if (!huellas.isEmpty()) {
            TrasConfirmar.ejecutar(() -> servicio.expulsarLectores(huellas));
        }
    }

    /** The last session of the camera ended: the agent stops publishing. */
    public void detenerSiNoQuedanSesiones(UUID camaraId) {
        if (accesos.abiertasDe(camaraId).isEmpty()) {
            transmisiones.deCamara(camaraId).ifPresent(transmision -> {
                transmisiones.eliminar(transmision);
                enviarTrasConfirmar(camaraId, new Detener());
            });
        }
    }

    /**
     * CA-22.1, CA-09.1: the camera may not stream (pause, consent revoked). Its sessions end now, the
     * agent stops publishing, and MediaMTX drops the publisher and every reader.
     */
    public void detenerCamara(UUID camaraId, Instant ahora) {
        bloquear(camaraId);
        finalizar(accesos.abiertasDe(camaraId), ahora);
        boolean transmitia = transmisiones
                .deCamara(camaraId)
                .map(transmision -> {
                    transmisiones.eliminar(transmision);
                    return true;
                })
                .orElse(false);
        Runnable detener = transmitia ? ruta(camaraId, new Detener()) : () -> {};
        TrasConfirmar.ejecutar(() -> {
            detener.run();
            servicio.expulsarCamara(camaraId);
        });
    }

    private void transmitir(UUID camaraId, String clave, ModoDeVista modo) {
        Transmitir mensaje =
                new Transmitir(propiedades.urlPublicacionDe(camaraId), PeticionDeMediaMtx.USUARIO_AGENTE, clave, modo);
        enviarTrasConfirmar(camaraId, mensaje);
    }
}
