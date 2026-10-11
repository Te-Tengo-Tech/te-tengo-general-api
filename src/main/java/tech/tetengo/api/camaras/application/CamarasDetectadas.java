package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.BusquedaDeCamarasPedida;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Ambiente;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-29 (product decision of 2026-10-10): the PC reports the webcams it detects; the owner chooses, from
 * the app, which ones are used and in which room («Elige las cámaras»), or stops using one. The PC
 * hears every change on its control channel ({@code CamaraActualizada}) and only cameras in use capture.
 * A webcam is known by the operating system's unique device id (CA-29.4); one that is unplugged keeps
 * its room (CA-29.5). The PC's own assignment ({@link CamarasDeLaPc#asignar}) stays as the installer's
 * fallback, with the same rules (CA-29.8); when both change a camera, the last change wins and the
 * activity log says where each came from. Invited members only read (CA-29.9).
 */
@Service
public class CamarasDetectadas {

    private final PcRepository pcs;
    private final CamaraRepository camaras;
    private final ServicioDeAmbientes ambientes;
    private final EmisionDeTokensDePc tokens;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public CamarasDetectadas(
            PcRepository pcs,
            CamaraRepository camaras,
            ServicioDeAmbientes ambientes,
            EmisionDeTokensDePc tokens,
            ApplicationEventPublisher eventos,
            Clock reloj) {
        this.pcs = pcs;
        this.camaras = camaras;
        this.ambientes = ambientes;
        this.tokens = tokens;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** A webcam as the PC reports it. */
    public record Deteccion(String dispositivoId, String nombreDispositivo, boolean conectada) {}

    /**
     * The PC's inventory, every time it starts and whenever a webcam is plugged in or out: new webcams
     * are added «sin usar», known ones are updated, and those missing from the report count as
     * unplugged (an assigned one keeps its room). Returns every webcam of the PC, with a per-camera token
     * for each one in use.
     */
    @Transactional
    public List<CamaraDeLaPc> reportar(UUID pcId, List<Deteccion> detectadas) {
        Pc pc = pc(pcId);
        Instant ahora = reloj.instant();
        Set<String> reportadas = new HashSet<>();
        for (Deteccion deteccion : detectadas) {
            if (!reportadas.add(deteccion.dispositivoId())) {
                continue;
            }
            camaras.deLaPcPorDispositivo(pc.getId(), deteccion.dispositivoId())
                    .ifPresentOrElse(
                            camara -> {
                                camara.reportarDeteccion(deteccion.nombreDispositivo(), deteccion.conectada(), ahora);
                                camaras.guardar(camara);
                            },
                            () -> camaras.guardar(Camara.detectada(
                                    pc.getId(),
                                    deteccion.dispositivoId(),
                                    deteccion.nombreDispositivo(),
                                    deteccion.conectada(),
                                    ahora)));
        }
        camaras.detectadasDeLaPc(pc.getId()).stream()
                .filter(camara -> !reportadas.contains(camara.getDispositivoId()))
                .forEach(camara -> {
                    camara.reportarDeteccion(null, false, ahora);
                    camaras.guardar(camara);
                });
        return conTokens(pc);
    }

    /** Every webcam of the PC, with tokens for the ones in use (the PC reconnects and asks again). */
    @Transactional(readOnly = true)
    public List<CamaraDeLaPc> deLaPcConTokens(UUID pcId) {
        return conTokens(pc(pcId));
    }

    /**
     * CA-29.6: «Buscar de nuevo»: the PC is asked, on its control channel, to look for webcams and report
     * them again. Owner only (the controller checks it).
     */
    @Transactional(readOnly = true)
    public void buscarDeNuevo(UUID pcId) {
        Pc pc = pc(pcId);
        eventos.publishEvent(new BusquedaDeCamarasPedida(pc.getHogarId(), pc.getId()));
    }

    /** «Elige las cámaras»: every webcam the PC detected, in use or not. Any member. */
    @Transactional(readOnly = true)
    public List<Camara> deLaPc(UUID pcId) {
        return camaras.detectadasDeLaPc(pc(pcId).getId());
    }

    /**
     * CA-29.2, from the app: the owner gives a detected webcam a room (by id, or a name that creates
     * it), or moves it. One camera per room (CA-29.3), no bathrooms.
     */
    @Transactional
    public Camara asignar(UUID camaraId, UUID ambienteId, String nombreAmbiente, UUID titularId) {
        Camara camara = detectada(camaraId);
        Ambiente ambiente = ambientes.elegido(ambienteId, nombreAmbiente);
        ambientes.exigirLibre(ambiente, camara);
        camara.asignarAmbiente(ambiente, titularId, reloj.instant());
        return camaras.guardar(camara);
    }

    /** «Dejar de usar» from the app: the webcam stays detected, «sin usar», and stops capturing. */
    @Transactional
    public void dejarDeUsar(UUID camaraId) {
        Camara camara = detectada(camaraId);
        if (camara.dejarDeUsar(reloj.instant())) {
            camaras.guardar(camara);
        }
    }

    private Camara detectada(UUID camaraId) {
        return camaras.buscarAunqueRetirada(camaraId)
                .filter(camara -> camara.getPcId() != null && camara.getDispositivoId() != null)
                .filter(camara -> pcs.buscar(camara.getPcId()).isPresent())
                .orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
    }

    private List<CamaraDeLaPc> conTokens(Pc pc) {
        return camaras.detectadasDeLaPc(pc.getId()).stream()
                .map(camara -> new CamaraDeLaPc(
                        camara, camara.enUso() ? tokens.deCamara(pc.getHogarId(), pc.getId(), camara.getId()) : null))
                .toList();
    }

    private Pc pc(UUID pcId) {
        return pcs.buscar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA));
    }
}
