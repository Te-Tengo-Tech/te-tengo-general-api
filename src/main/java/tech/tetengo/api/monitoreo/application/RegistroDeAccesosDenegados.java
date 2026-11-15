package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.monitoreo.application.port.AccesoDenegadoRepository;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.CanalDeAcceso;
import tech.tetengo.api.monitoreo.domain.model.IntentoDenegado;
import tech.tetengo.api.monitoreo.domain.model.MotivoDeDenegacion;
import tech.tetengo.api.monitoreo.domain.model.PeticionDeMediaMtx;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * Records every denied attempt to reach a camera's live view (ADR 0015), filed under the camera's
 * household so that household can see it. Each denial is written in its own transaction
 * ({@code REQUIRES_NEW}): the use cases deny by throwing {@code ErrorDeNegocio} inside their own
 * transaction, which then rolls back, and the denial must outlive it. A failure to record is logged and
 * never changes the answer: the request is denied either way.
 */
@Service
public class RegistroDeAccesosDenegados {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeAccesosDenegados.class);

    private final AccesoDenegadoRepository denegados;
    private final AccesoVistaEnVivoRepository accesos;
    private final CamarasDelHogar camaras;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public RegistroDeAccesosDenegados(
            AccesoDenegadoRepository denegados,
            AccesoVistaEnVivoRepository accesos,
            CamarasDelHogar camaras,
            PlatformTransactionManager transacciones,
            Clock reloj) {
        this.denegados = denegados;
        this.accesos = accesos;
        this.camaras = camaras;
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reloj = reloj;
    }

    /**
     * A member's request to the API was refused on that camera; the member's household is the one in
     * context. {@code CAMARA_NO_ENCONTRADA} is recorded only when the camera is not of that household (of
     * another one, or of none): a camera of their own that is not in use, such as a webcam «sin usar», is
     * not a denial.
     */
    public void enApi(MotivoDeDenegacion motivo, UUID camaraId, UUID usuarioId) {
        UUID hogarDelUsuario = HogarActual.obtener().orElse(null);
        registrar(
                () -> Optional.of(new IntentoDenegado(
                        reloj.instant(), CanalDeAcceso.API, motivo, null, null, camaraId, usuarioId, hogarDelUsuario)),
                motivo == MotivoDeDenegacion.CAMARA_NO_ENCONTRADA);
    }

    /**
     * A member named a live view session that is not theirs: recorded as {@code SESION_AJENA} on the
     * session's camera when the session exists and another user opened it. One of their own that ended,
     * or an id of no session, is not a denial and is not recorded.
     */
    public void siLaSesionEsAjena(UUID sesionId, UUID usuarioId) {
        UUID hogarDelUsuario = HogarActual.obtener().orElse(null);
        registrar(
                () -> accesos.deCualquierHogar(sesionId)
                        .filter(sesion -> !sesion.usuarioId().equals(usuarioId))
                        .map(sesion -> new IntentoDenegado(
                                reloj.instant(),
                                CanalDeAcceso.API,
                                MotivoDeDenegacion.SESION_AJENA,
                                null,
                                null,
                                sesion.camaraId(),
                                usuarioId,
                                hogarDelUsuario)),
                false);
    }

    /**
     * MediaMTX's hook refused a publish or a read. {@code usuarioId} and {@code hogarDelUsuario}: the
     * viewer of the session whose token was used, when the token named one.
     */
    public void enMediaMtx(
            MotivoDeDenegacion motivo, PeticionDeMediaMtx peticion, UUID usuarioId, UUID hogarDelUsuario) {
        registrar(
                () -> Optional.of(new IntentoDenegado(
                        reloj.instant(),
                        CanalDeAcceso.MEDIAMTX,
                        motivo,
                        peticion.accion(),
                        peticion.protocolo(),
                        peticion.camara().orElse(null),
                        usuarioId,
                        hogarDelUsuario)),
                false);
    }

    /**
     * Records the attempt, if any, under its camera's household. {@code soloDeOtroHogar}: skip it when the
     * camera is of the requester's own household.
     */
    private void registrar(Supplier<Optional<IntentoDenegado>> intento, boolean soloDeOtroHogar) {
        try {
            transaccion.executeWithoutResult(estado -> intento.get().ifPresent(i -> {
                UUID hogar = i.camaraId() == null
                        ? null
                        : camaras.hogarDeCamara(i.camaraId()).orElse(null);
                if (soloDeOtroHogar && hogar != null && hogar.equals(i.hogarDelUsuario())) {
                    return;
                }
                denegados.registrar(i, hogar);
            }));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar un acceso denegado a la vista en vivo", e);
        }
    }
}
