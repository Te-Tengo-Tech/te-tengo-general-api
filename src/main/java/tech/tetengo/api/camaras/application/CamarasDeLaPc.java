package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Ambiente;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-29 on the linked PC: the webcams it uses, each in a room of the household. The PC lists every
 * webcam it finds (CA-29.1) and only tells the API about the ones the family chose, by the device id
 * the operating system gives them (CA-29.4). Each camera in use gets a per-camera token, so the
 * heartbeat, events, clips and live view work as with an installation.
 */
@Service
public class CamarasDeLaPc {

    private final PcRepository pcs;
    private final CamaraRepository camaras;
    private final ServicioDeAmbientes ambientes;
    private final EmisionDeTokensDePc tokens;
    private final Clock reloj;

    public CamarasDeLaPc(
            PcRepository pcs,
            CamaraRepository camaras,
            ServicioDeAmbientes ambientes,
            EmisionDeTokensDePc tokens,
            Clock reloj) {
        this.pcs = pcs;
        this.camaras = camaras;
        this.ambientes = ambientes;
        this.tokens = tokens;
        this.reloj = reloj;
    }

    /** The PC's cameras in use. */
    @Transactional(readOnly = true)
    public List<Camara> listar(UUID pcId) {
        return camaras.deLaPc(pc(pcId).getId());
    }

    /**
     * CA-29.2: «Usar esta cámara» in a room, or move it to another room. The room is chosen by id or by
     * name (a new name creates it); it may not have another camera in use (CA-29.3). The same webcam is
     * always the same camera, also after «Dejar de usar» (CA-29.4).
     */
    @Transactional
    public CamaraDeLaPc asignar(
            UUID pcId, String dispositivoId, String nombreDispositivo, UUID ambienteId, String nombreAmbiente) {
        Pc pc = pc(pcId);
        Instant ahora = reloj.instant();
        Ambiente ambiente = ambientes.elegido(ambienteId, nombreAmbiente);
        Camara camara = camaras.deLaPcPorDispositivo(pc.getId(), dispositivoId).orElse(null);
        ambientes.exigirLibre(ambiente, camara);
        if (camara == null) {
            camara = Camara.deDispositivo(
                    pc.getHogarId(), pc.getId(), dispositivoId, nombreDispositivo, ambiente, null, ahora);
        } else {
            camara.actualizarDispositivo(nombreDispositivo);
            camara.asignarAmbiente(ambiente, null, ahora);
        }
        Camara guardada = camaras.guardar(camara);
        return new CamaraDeLaPc(guardada, tokens.deCamara(pc.getHogarId(), pc.getId(), guardada.getId()));
    }

    /** «Dejar de usar»: the camera stops watching its room; its token stops working. */
    @Transactional
    public void dejarDeUsar(UUID pcId, UUID camaraId) {
        Pc pc = pc(pcId);
        Camara camara = camaras.buscar(camaraId)
                .filter(c -> pc.getId().equals(c.getPcId()))
                .orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
        if (camara.dejarDeUsar(reloj.instant())) {
            camaras.guardar(camara);
        }
    }

    /** New per-camera tokens for every camera in use of the PC, e.g. when they are about to expire. */
    @Transactional(readOnly = true)
    public List<CamaraDeLaPc> renovarTokens(UUID pcId) {
        Pc pc = pc(pcId);
        return camaras.deLaPc(pc.getId()).stream()
                .map(camara -> new CamaraDeLaPc(camara, tokens.deCamara(pc.getHogarId(), pc.getId(), camara.getId())))
                .toList();
    }

    private Pc pc(UUID pcId) {
        return pcs.buscar(pcId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.PC_NO_ENCONTRADA));
    }
}
