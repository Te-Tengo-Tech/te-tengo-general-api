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
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;
import tech.tetengo.api.monitoreo.domain.model.MotivoDeDenegacion;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * MediaMTX asks before every publish and every new reader (API contract §4, ADR 0007):
 *
 * <ul>
 *   <li>the request must come from the private networks and carry the shared secret;
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
 * <p>Every denial is recorded with its reason in the denied access log (ADR 0015). An RTSP client first
 * announces without credentials and sends them only after MediaMTX answers 401 (RFC 2617 Basic), so every
 * publish starts with a credential-less request: it is denied without being logged or recorded as a
 * denial. MediaMTX 1.21 asks one authorization at a time and waits up to its {@code readTimeout} (10 s) for
 * each, so a slow answer delays every other publish and read: answers slower than {@link #LENTA} are
 * logged.
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
    private final RegistroDeAccesosDenegados denegados;
    private final Clock reloj;

    public AutorizarMediaMtx(
            AccesoVistaEnVivoRepository accesos,
            TransmisionEnVivoRepository transmisiones,
            CamarasDelHogar camaras,
            EjecutorEnHogar enHogar,
            PropiedadesDeVistaEnVivo propiedades,
            RegistroDeAccesosDenegados denegados,
            Clock reloj) {
        this.accesos = accesos;
        this.transmisiones = transmisiones;
        this.camaras = camaras;
        this.enHogar = enHogar;
        this.propiedades = propiedades;
        this.denegados = denegados;
        this.reloj = reloj;
    }

    /** True if MediaMTX may let the request through; the request came from the private networks. */
    public boolean ejecutar(String secreto, PeticionDeMediaMtx peticion) {
        return ejecutar(secreto, peticion, true);
    }

    /** True if MediaMTX may let the request through; {@code desdeRedInterna} false always denies. */
    public boolean ejecutar(String secreto, PeticionDeMediaMtx peticion, boolean desdeRedInterna) {
        long inicio = System.nanoTime();
        try {
            return autorizar(secreto, peticion, desdeRedInterna);
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

    private boolean autorizar(String secreto, PeticionDeMediaMtx peticion, boolean desdeRedInterna) {
        Decision decision = decidir(secreto, peticion, desdeRedInterna);
        if (decision.permitida()) {
            return true;
        }
        MotivoDeDenegacion motivo = decision.motivo();
        if (motivo != MotivoDeDenegacion.ORIGEN_EXTERNO
                && motivo != MotivoDeDenegacion.SECRETO_INVALIDO
                && peticion.tipo() == PeticionDeMediaMtx.Tipo.PUBLICAR
                && peticion.sinCredenciales()) {
            // The RTSP client asks again with them once MediaMTX answers 401: not a denial.
            log.debug(
                    "MediaMTX: {} ({}) sin credenciales en {}",
                    peticion.accion(),
                    peticion.protocolo(),
                    peticion.ruta());
            return false;
        }
        if (motivo == MotivoDeDenegacion.SECRETO_INVALIDO) {
            log.info("MediaMTX: {} ({}) sin el secreto del gancho", peticion.accion(), peticion.protocolo());
        } else {
            log.info(
                    "MediaMTX: {} ({}) denegado en {}: {}",
                    peticion.accion(),
                    peticion.protocolo(),
                    peticion.ruta(),
                    motivo);
        }
        denegados.enMediaMtx(motivo, peticion, decision.usuarioId(), decision.hogarDelUsuario());
        return false;
    }

    private Decision decidir(String secreto, PeticionDeMediaMtx peticion, boolean desdeRedInterna) {
        if (!desdeRedInterna) {
            return Decision.denegada(MotivoDeDenegacion.ORIGEN_EXTERNO);
        }
        if (!secretoValido(secreto)) {
            return Decision.denegada(MotivoDeDenegacion.SECRETO_INVALIDO);
        }
        if (peticion.tipo() == PeticionDeMediaMtx.Tipo.OTRA) {
            return Decision.denegada(
                    "read".equals(peticion.accion())
                            ? MotivoDeDenegacion.PROTOCOLO_NO_PERMITIDO
                            : MotivoDeDenegacion.ACCION_NO_PERMITIDA);
        }
        Optional<UUID> camara = peticion.camara();
        if (camara.isEmpty()) {
            return Decision.denegada(MotivoDeDenegacion.RUTA_INVALIDA);
        }
        return peticion.tipo() == PeticionDeMediaMtx.Tipo.PUBLICAR
                ? publicar(camara.get(), peticion)
                : leer(camara.get(), peticion);
    }

    private Decision publicar(UUID camaraId, PeticionDeMediaMtx peticion) {
        return peticion.claveDelAgente()
                .flatMap(clave -> transmisiones.hogarDeClave(camaraId, Secretos.huella(clave)))
                .map(hogar -> enHogar.obtener(hogar, () -> publicacion(camaraId, reloj.instant())))
                .orElse(Decision.denegada(MotivoDeDenegacion.CLAVE_DE_PUBLICACION_INVALIDA));
    }

    /**
     * A camera in use (or one without a PC) publishes while it may capture; a webcam «sin usar» only
     * while a preview of it is open, so its PC cannot stream it once the preview ended.
     */
    private Decision publicacion(UUID camaraId, Instant ahora) {
        Optional<CamaraDetectada> detectada = camaras.buscarDetectada(camaraId);
        if (detectada.isPresent() && !detectada.get().enUso()) {
            if (!detectada.get().capturaPermitida()) {
                return Decision.denegada(MotivoDeDenegacion.CAPTURA_NO_PERMITIDA);
            }
            return accesos.abiertasDe(camaraId).stream()
                            .anyMatch(acceso -> acceso.isVistaPrevia() && acceso.admiteLectura(ahora))
                    ? Decision.PERMITIDA
                    : Decision.denegada(MotivoDeDenegacion.PUBLICACION_SIN_VISTA_PREVIA);
        }
        return capturaPermitida(camaraId, detectada)
                ? Decision.PERMITIDA
                : Decision.denegada(MotivoDeDenegacion.CAPTURA_NO_PERMITIDA);
    }

    private Decision leer(UUID camaraId, PeticionDeMediaMtx peticion) {
        Optional<String> token = peticion.tokenDeEspectador();
        if (token.isEmpty()) {
            return Decision.denegada(MotivoDeDenegacion.SIN_TOKEN);
        }
        Optional<SesionDeToken> sesion = accesos.porToken(Secretos.huella(token.get()));
        if (sesion.isEmpty()) {
            return Decision.denegada(MotivoDeDenegacion.TOKEN_INVALIDO);
        }
        UUID hogar = sesion.get().hogarId();
        Instant ahora = reloj.instant();
        return enHogar.obtener(
                hogar,
                () -> accesos.buscar(sesion.get().sesionId())
                        .map(acceso -> lectura(camaraId, acceso, ahora).de(acceso.getUsuarioId(), hogar))
                        .orElse(Decision.denegada(MotivoDeDenegacion.TOKEN_INVALIDO)));
    }

    private Decision lectura(UUID camaraId, AccesoVistaEnVivo acceso, Instant ahora) {
        if (!acceso.getCamaraId().equals(camaraId)) {
            return Decision.denegada(MotivoDeDenegacion.TOKEN_DE_OTRA_CAMARA);
        }
        if (!acceso.admiteLectura(ahora)) {
            return Decision.denegada(MotivoDeDenegacion.SESION_TERMINADA);
        }
        Optional<CamaraDetectada> detectada = camaras.buscarDetectada(camaraId);
        // A webcam «sin usar» is only watched through its preview (CA-29.7).
        if (detectada.map(camara -> !camara.enUso()).orElse(false) && !acceso.isVistaPrevia()) {
            return Decision.denegada(MotivoDeDenegacion.LECTURA_SIN_VISTA_PREVIA);
        }
        if (!capturaPermitida(camaraId, detectada)) {
            return Decision.denegada(MotivoDeDenegacion.CAPTURA_NO_PERMITIDA);
        }
        acceso.registrarLectura(ahora);
        accesos.guardar(acceso);
        return Decision.PERMITIDA;
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

    /**
     * The answer to MediaMTX: allowed without {@code motivo}; otherwise why not and, when the viewer token
     * named a session, whose it was.
     */
    private record Decision(MotivoDeDenegacion motivo, UUID usuarioId, UUID hogarDelUsuario) {

        static final Decision PERMITIDA = new Decision(null, null, null);

        static Decision denegada(MotivoDeDenegacion motivo) {
            return new Decision(motivo, null, null);
        }

        boolean permitida() {
            return motivo == null;
        }

        Decision de(UUID usuario, UUID hogar) {
            return permitida() ? this : new Decision(motivo, usuario, hogar);
        }
    }
}
