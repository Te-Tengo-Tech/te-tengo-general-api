package tech.tetengo.api.actividad.application;

import static tech.tetengo.api.actividad.application.RegistroDeActividad.clave;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tech.tetengo.api.actividad.domain.model.Actividad;
import tech.tetengo.api.actividad.domain.model.OrigenDeActividad;
import tech.tetengo.api.actividad.domain.model.TipoDeActividad;
import tech.tetengo.api.camaras.AmbienteRenombrado;
import tech.tetengo.api.camaras.CamaraAsignada;
import tech.tetengo.api.camaras.CamaraRetirada;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.DatosDeLaPcBorrados;
import tech.tetengo.api.camaras.PcDesvinculada;
import tech.tetengo.api.camaras.PcRenombrada;
import tech.tetengo.api.camaras.PcVinculada;
import tech.tetengo.api.hogares.ConsentimientoOtorgado;
import tech.tetengo.api.hogares.ConsentimientoRevocado;
import tech.tetengo.api.monitoreo.CamaraPausada;
import tech.tetengo.api.monitoreo.CamaraReanudada;
import tech.tetengo.api.monitoreo.PausaFinalizada;
import tech.tetengo.api.movimientos.ConsentimientoDeMovimientosOtorgado;
import tech.tetengo.api.movimientos.ConsentimientoDeMovimientosRevocado;
import tech.tetengo.api.movimientos.RetencionDeMovimientosCambiada;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;

/**
 * US-33 / CA-33.1: the changes the other modules publish become entries of the activity log, with who
 * made them (a member, a PC, or the system), what changed, before and after, and when. Listeners run
 * after the change commits; an event delivered twice is stored once.
 */
@Service
public class ActividadDeLosModulos {

    private final RegistroDeActividad registro;
    private final CamarasDelHogar camaras;
    private final EjecutorEnHogar enHogar;

    public ActividadDeLosModulos(RegistroDeActividad registro, CamarasDelHogar camaras, EjecutorEnHogar enHogar) {
        this.registro = registro;
        this.camaras = camaras;
        this.enHogar = enHogar;
    }

    private void registrar(UUID hogarId, Supplier<Actividad> entrada) {
        enHogar.ejecutar(hogarId, () -> registro.registrar(entrada.get()));
    }

    private static OrigenDeActividad familiarOSistema(UUID usuarioId) {
        return usuarioId == null ? OrigenDeActividad.SISTEMA : OrigenDeActividad.FAMILIAR;
    }

    @Async
    @TransactionalEventListener
    public void pcVinculada(PcVinculada e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("PC_VINCULADA", e.pcId()),
                                e.ocurridaEn(),
                                TipoDeActividad.PC_VINCULADA,
                                OrigenDeActividad.FAMILIAR)
                        .porUsuario(e.usuarioId())
                        .enPc(e.pcId(), e.nombre())
                        .sobre(e.nombre(), null, null));
    }

    @Async
    @TransactionalEventListener
    public void pcRenombrada(PcRenombrada e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("PC_RENOMBRADA", e.pcId(), e.ocurridaEn(), e.nombreNuevo()),
                                e.ocurridaEn(),
                                TipoDeActividad.PC_RENOMBRADA,
                                familiarOSistema(e.usuarioId()))
                        .porUsuario(e.usuarioId())
                        .enPc(e.pcId(), e.nombreNuevo())
                        .sobre("PC", e.nombreAnterior(), e.nombreNuevo()));
    }

    /** From the app, from the PC itself, or by the system (stolen token, 90 days). */
    @Async
    @TransactionalEventListener
    public void pcDesvinculada(PcDesvinculada e) {
        OrigenDeActividad origen =
                switch (e.motivo()) {
                    case "DESDE_LA_APP" -> OrigenDeActividad.FAMILIAR;
                    case "DESDE_LA_PC" -> OrigenDeActividad.PC;
                    default -> OrigenDeActividad.SISTEMA;
                };
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("PC_DESVINCULADA", e.pcId()),
                                e.ocurridaEn(),
                                TipoDeActividad.PC_DESVINCULADA,
                                origen)
                        .porUsuario(e.usuarioId())
                        .enPc(e.pcId(), e.nombre())
                        .sobre(e.nombre(), String.join(", ", e.habitaciones()), e.motivo()));
    }

    /** Done on the PC (no member) or by the owner in the app. */
    @Async
    @TransactionalEventListener
    public void camaraAsignada(CamaraAsignada e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("CAMARA_ASIGNADA", e.camaraId(), e.ocurridaEn(), e.ambienteNuevo()),
                                e.ocurridaEn(),
                                TipoDeActividad.CAMARA_ASIGNADA,
                                e.usuarioId() == null ? OrigenDeActividad.PC : OrigenDeActividad.FAMILIAR)
                        .porUsuario(e.usuarioId())
                        .enPc(
                                e.pcId(),
                                e.pcId() == null
                                        ? null
                                        : camaras.nombreDePc(e.pcId()).orElse(null))
                        .deCamara(e.camaraId())
                        .sobre(e.nombreDispositivo(), e.ambienteAnterior(), e.ambienteNuevo()));
    }

    @Async
    @TransactionalEventListener
    public void camaraRetirada(CamaraRetirada e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("CAMARA_RETIRADA", e.camaraId(), e.ocurridaEn()),
                                e.ocurridaEn(),
                                TipoDeActividad.CAMARA_RETIRADA,
                                e.usuarioId() == null ? OrigenDeActividad.PC : OrigenDeActividad.FAMILIAR)
                        .porUsuario(e.usuarioId())
                        .enPc(
                                e.pcId(),
                                e.pcId() == null
                                        ? null
                                        : camaras.nombreDePc(e.pcId()).orElse(null))
                        .deCamara(e.camaraId())
                        .sobre(e.nombreDispositivo(), e.ambiente(), null));
    }

    /** CA-31.6: unlinked from the PC, which also deleted its data folder. */
    @Async
    @TransactionalEventListener
    public void datosDeLaPcBorrados(DatosDeLaPcBorrados e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("DATOS_LOCALES_ELIMINADOS", e.pcId(), e.ocurridaEn()),
                                e.ocurridaEn(),
                                TipoDeActividad.DATOS_LOCALES_ELIMINADOS,
                                OrigenDeActividad.PC)
                        .enPc(e.pcId(), e.nombre())
                        .sobre("Datos guardados en la PC", null, "Borrados al desvincular"));
    }

    @Async
    @TransactionalEventListener
    public void ambienteRenombrado(AmbienteRenombrado e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("AMBIENTE_RENOMBRADO", e.ambienteId(), e.ocurridaEn(), e.nombreNuevo()),
                                e.ocurridaEn(),
                                TipoDeActividad.AMBIENTE_RENOMBRADO,
                                familiarOSistema(e.usuarioId()))
                        .porUsuario(e.usuarioId())
                        .sobre("Ambiente", e.nombreAnterior(), e.nombreNuevo()));
    }

    @Async
    @TransactionalEventListener
    public void camaraPausada(CamaraPausada e) {
        registrar(
                e.hogarId(),
                () -> conPcDeCamara(
                                Actividad.de(
                                                clave("CAMARA_PAUSADA", e.camaraId(), e.ocurridaEn()),
                                                e.ocurridaEn(),
                                                TipoDeActividad.CAMARA_PAUSADA,
                                                familiarOSistema(e.usuarioId()))
                                        .porUsuario(e.usuarioId()),
                                e.camaraId())
                        .sobre(
                                e.habitacion(),
                                null,
                                e.hasta() == null ? null : e.hasta().toString()));
    }

    @Async
    @TransactionalEventListener
    public void camaraReanudada(CamaraReanudada e) {
        registrar(
                e.hogarId(),
                () -> conPcDeCamara(
                                Actividad.de(
                                                clave("CAMARA_REANUDADA", e.camaraId(), e.ocurridaEn()),
                                                e.ocurridaEn(),
                                                TipoDeActividad.CAMARA_REANUDADA,
                                                familiarOSistema(e.usuarioId()))
                                        .porUsuario(e.usuarioId()),
                                e.camaraId())
                        .sobre(e.habitacion(), null, null));
    }

    /** CA-22.3: the pause ended on its own. */
    @Async
    @TransactionalEventListener
    public void pausaFinalizada(PausaFinalizada e) {
        registrar(
                e.hogarId(),
                () -> conPcDeCamara(
                                Actividad.de(
                                        clave("CAMARA_REANUDADA", e.camaraId(), e.finalizadaEn()),
                                        e.finalizadaEn(),
                                        TipoDeActividad.CAMARA_REANUDADA,
                                        OrigenDeActividad.SISTEMA),
                                e.camaraId())
                        .sobre(e.habitacion(), null, null));
    }

    @Async
    @TransactionalEventListener
    public void consentimientoOtorgado(ConsentimientoOtorgado e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("CONSENTIMIENTO_OTORGADO", e.otorgadoEn()),
                                e.otorgadoEn(),
                                TipoDeActividad.CONSENTIMIENTO_OTORGADO,
                                familiarOSistema(e.usuarioId()))
                        .porUsuario(e.usuarioId())
                        .sobre("Consentimiento de monitoreo", null, e.otorgadoPor()));
    }

    @Async
    @TransactionalEventListener
    public void consentimientoRevocado(ConsentimientoRevocado e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("CONSENTIMIENTO_REVOCADO", e.revocadoEn()),
                                e.revocadoEn(),
                                TipoDeActividad.CONSENTIMIENTO_REVOCADO,
                                familiarOSistema(e.usuarioId()))
                        .porUsuario(e.usuarioId())
                        .sobre("Consentimiento de monitoreo", null, null));
    }

    @Async
    @TransactionalEventListener
    public void consentimientoDeMovimientosOtorgado(ConsentimientoDeMovimientosOtorgado e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("CONSENTIMIENTO_MOVIMIENTOS_OTORGADO", e.ocurridoEn()),
                                e.ocurridoEn(),
                                TipoDeActividad.CONSENTIMIENTO_MOVIMIENTOS_OTORGADO,
                                familiarOSistema(e.registradoPor()))
                        .porUsuario(e.registradoPor())
                        .sobre(
                                "Guardar movimientos",
                                null,
                                "%s (versión %s)".formatted(e.otorgadoPor(), e.versionTexto())));
    }

    /** By the owner, or by the system when the monitoring consent was revoked. */
    @Async
    @TransactionalEventListener
    public void consentimientoDeMovimientosRevocado(ConsentimientoDeMovimientosRevocado e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("CONSENTIMIENTO_MOVIMIENTOS_REVOCADO", e.ocurridoEn()),
                                e.ocurridoEn(),
                                TipoDeActividad.CONSENTIMIENTO_MOVIMIENTOS_REVOCADO,
                                familiarOSistema(e.usuarioId()))
                        .porUsuario(e.usuarioId())
                        .sobre("Guardar movimientos", null, e.motivo()));
    }

    @Async
    @TransactionalEventListener
    public void retencionCambiada(RetencionDeMovimientosCambiada e) {
        registrar(
                e.hogarId(),
                () -> Actividad.de(
                                clave("RETENCION_MOVIMIENTOS_CAMBIADA", e.ocurridaEn(), e.diasNuevos()),
                                e.ocurridaEn(),
                                TipoDeActividad.RETENCION_MOVIMIENTOS_CAMBIADA,
                                familiarOSistema(e.usuarioId()))
                        .porUsuario(e.usuarioId())
                        .sobre("Movimientos en la nube", dias(e.diasAnteriores()), dias(e.diasNuevos())));
    }

    private Actividad conPcDeCamara(Actividad entrada, UUID camaraId) {
        return camaras.pcDeCamara(camaraId)
                .map(pc -> entrada.enPc(pc, camaras.nombreDePc(pc).orElse(null)))
                .orElse(entrada)
                .deCamara(camaraId);
    }

    /** «No guardar» or «30 días». */
    private static String dias(Integer dias) {
        return dias == null ? "No guardar" : dias + " días";
    }
}
