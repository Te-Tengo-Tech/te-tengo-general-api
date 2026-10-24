package tech.tetengo.api.monitoreo.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDetectada;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository.SesionDeToken;
import tech.tetengo.api.monitoreo.application.port.TransmisionEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * MediaMTX asks before every publish and every new reader (API contract §4, ADR 0007):
 *
 * <ul>
 *   <li>the request must carry the shared secret;
 *   <li>{@code publish} only on {@code camaras/<camaraId>}, as user {@code agente} with the publish token of
 *       the camera's current transmission, which only the camera's agent (or, for a webcam «sin usar»,
 *       its PC) received; a webcam «sin usar» publishes only while a preview of it is open (US-29,
 *       CA-29.7);
 *   <li>{@code read} over HLS or WebRTC (WHEP), and {@code playback}, only with the viewer token
 *       ({@code ?token=}) of an open session of that camera, which also counts as the viewer's activity;
 *       a webcam «sin usar» is read only with the token of its preview; a read over any other protocol
 *       is denied;
 *   <li>never while the camera may not capture (pause, no consent);
 *   <li>anything else ({@code api}, {@code metrics}, {@code pprof}) is denied.
 * </ul>
 *
 * <p>An RTSP client first announces without credentials and sends them only after MediaMTX answers 401
 * (RFC 2617 Basic), so every publish starts with a credential-less request: it is denied without being
 * logged as a denial. MediaMTX 1.21 asks one authorization at a time and waits up to its
 * {@code readTimeout} (10 s) for each, so a slow answer delays every other publish and read: answers
 * slower than {@link #LENTA} are logged.
 */
@Service
public class AutorizarMediaMtx {

    private static final Logger log = LoggerFactory.getLogger(AutorizarMediaMtx.class);

    /** An answer slower than this is logged: MediaMTX holds every other publish and read meanwhile. */
    static final Duration LENTA = Duration.ofSeconds(1);

    private final AccesoVistaEnVivoRepository accesos;
    private final TransmisionEnVivoRepository transmisiones;
    private final CamarasDelHogar camaras;
    private final EjecutorEnHogar enHogar;
    private final PropiedadesDeVistaEnVivo propiedades;
    private final Clock reloj;

    public AutorizarMediaMtx(
            AccesoVistaEnVivoRepository accesos,
            TransmisionEnVivoRepository transmisiones,
            CamarasDelHogar camaras,
            EjecutorEnHogar enHogar,
            PropiedadesDeVistaEnVivo propiedades,
            Clock reloj) {
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.camaras = camaras;
        this.enHogar = enHogar;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** True if MediaMTX may let the request through. */
    public boolean ejecutar(String secreto, PeticionDeMediaMtx peticion) {
        long inicio = System.nanoTime();
        try {
            return autorizar(secreto, peticion);
        } finally {
            Duration demora = Duration.ofNanos(System.nanoTime() - inicio);
            if (demora.compareTo(LENTA) > 0) {
                log.warn(
                        "MediaMTX: autorizar {} ({}) en {} tardó {} ms; MediaMTX no atiende otras lecturas ni"
                                + " publicaciones mientras espera",
                        peticion.accion(),
                        peticion.protocolo(),
                        peticion.ruta(),
                        demora.toMillis());
            }
        }
    }

    private boolean autorizar(String secreto, PeticionDeMediaMtx peticion) {
        if (!secretoValido(secreto)) {
            log.info("MediaMTX: {} ({}) sin el secreto del gancho", peticion.accion(), peticion.protocolo());
            return false;
        }
        Optional<UUID> camara = peticion.camara();
        if (camara.isEmpty()) {
            return false;
        }
        boolean permitido =
                switch (peticion.tipo()) {
                    case PUBLICAR -> publicar(camara.get(), peticion);
                    case LEER -> leer(camara.get(), peticion);
                    case OTRA -> false;
                };
        if (!permitido) {
            if (peticion.tipo() == PeticionDeMediaMtx.Tipo.PUBLICAR && peticion.sinCredenciales()) {
                // The RTSP client asks again with them once MediaMTX answers 401.
                log.debug(
                        "MediaMTX: {} ({}) sin credenciales en {}",
                        peticion.accion(),
                        peticion.protocolo(),
                        peticion.ruta());
            } else {
                log.info("MediaMTX: {} ({}) denegado en {}", peticion.accion(), peticion.protocolo(), peticion.ruta());
            }
        }
        return permitido;
    }

    private boolean publicar(UUID camaraId, PeticionDeMediaMtx peticion) {
        return peticion.claveDelAgente()
                .flatMap(clave -> transmisiones.hogarDeClave(camaraId, Secretos.huella(clave)))
                .map(hogar -> enHogar.obtener(hogar, () -> publicacionPermitida(camaraId, reloj.instant())))
                .orElse(false);
    }

    /**
     * A camera in use (or one without a PC) publishes while it may capture; a webcam «sin usar» only
     * while a preview of it is open, so its PC cannot stream it once the preview ended.
     */
    private boolean publicacionPermitida(UUID camaraId, Instant ahora) {
        Optional<CamaraDetectada> detectada = camaras.buscarDetectada(camaraId);
        if (detectada.isPresent() && !detectada.get().enUso()) {
            return detectada.get().capturaPermitida()
                    && accesos.abiertasDe(camaraId).stream()
                            .anyMatch(acceso -> acceso.isVistaPrevia() && acceso.admiteLectura(ahora));
        }
        return capturaPermitida(camaraId, detectada);
    }

    private boolean leer(UUID camaraId, PeticionDeMediaMtx peticion) {
        Instant ahora = reloj.instant();
        Optional<SesionDeToken> sesion =
                peticion.tokenDeEspectador().flatMap(token -> accesos.porToken(Secretos.huella(token)));
        return sesion.map(s -> enHogar.obtener(s.hogarId(), () -> {
                    Optional<CamaraDetectada> detectada = camaras.buscarDetectada(camaraId);
                    boolean sinUsar = detectada.map(camara -> !camara.enUso()).orElse(false);
                    return accesos.buscar(s.sesionId())
                            .filter(acceso -> acceso.getCamaraId().equals(camaraId))
                            .filter(acceso -> acceso.admiteLectura(ahora))
                            // A webcam «sin usar» is only watched through its preview (CA-29.7).
                            .filter(acceso -> !sinUsar || acceso.isVistaPrevia())
                            .filter(acceso -> capturaPermitida(camaraId, detectada))
                            .map(acceso -> {
                                acceso.registrarLectura(ahora);
                                accesos.guardar(acceso);
                                return true;
                            })
                            .orElse(false);
                }))
                .orElse(false);
    }

    private boolean capturaPermitida(UUID camaraId, Optional<CamaraDetectada> detectada) {
        // A webcam of a linked PC, in use or being previewed (US-29); otherwise a camera without a PC.
        return detectada
                .map(CamaraDetectada::capturaPermitida)
                .or(() -> camaras.buscar(camaraId).map(CamaraDelHogar::capturaPermitida))
                .orElse(false);
    }

    private boolean secretoValido(String secreto) {
        String esperado = propiedades.secretoAutorizacion();
        if (esperado == null || esperado.isBlank() || secreto == null) {
            return false;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), secreto.getBytes(StandardCharsets.UTF_8));
    }
}
