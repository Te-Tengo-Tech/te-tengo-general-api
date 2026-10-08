package tech.tetengo.api.monitoreo.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.CamaraDelHogar;
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
 *       the camera's current transmission;
 *   <li>{@code read} and {@code playback} only with the viewer token of an open session of that camera,
 *       which also counts as the viewer's activity;
 *   <li>never while the camera may not capture (pause, no consent);
 *   <li>anything else ({@code api}, {@code metrics}, {@code pprof}) is denied.
 * </ul>
 */
@Service
public class AutorizarMediaMtx {

    private static final Logger log = LoggerFactory.getLogger(AutorizarMediaMtx.class);

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
        if (!secretoValido(secreto)) {
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
            log.info("MediaMTX: {} denegado en {}", peticion.accion(), peticion.ruta());
        }
        return permitido;
    }

    private boolean publicar(UUID camaraId, PeticionDeMediaMtx peticion) {
        return peticion.claveDelAgente()
                .flatMap(clave -> transmisiones.hogarDeClave(camaraId, Secretos.huella(clave)))
                .map(hogar -> enHogar.obtener(hogar, () -> capturaPermitida(camaraId)))
                .orElse(false);
    }

    private boolean leer(UUID camaraId, PeticionDeMediaMtx peticion) {
        Instant ahora = reloj.instant();
        Optional<SesionDeToken> sesion =
                peticion.tokenDeEspectador().flatMap(token -> accesos.porToken(Secretos.huella(token)));
        return sesion.map(s -> enHogar.obtener(
                        s.hogarId(),
                        () -> accesos.buscar(s.sesionId())
                                .filter(acceso -> acceso.getCamaraId().equals(camaraId))
                                .filter(acceso -> acceso.admiteLectura(ahora))
                                .filter(acceso -> capturaPermitida(camaraId))
                                .map(acceso -> {
                                    acceso.registrarLectura(ahora);
                                    accesos.guardar(acceso);
                                    return true;
                                })
                                .orElse(false)))
                .orElse(false);
    }

    private boolean capturaPermitida(UUID camaraId) {
        return camaras.buscar(camaraId).map(CamaraDelHogar::capturaPermitida).orElse(false);
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
