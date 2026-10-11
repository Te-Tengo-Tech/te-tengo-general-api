package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.EstadoDeCapturaRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.EstadoConexion;
import tech.tetengo.api.camaras.domain.model.EstadoDeCaptura;

@Service
public class ServicioDeCamaras implements CamarasDelHogar {

    private final CamaraRepository camaras;
    private final PcRepository pcs;
    private final ConsultarEstadoDeCaptura estadoDeCaptura;
    private final EstadoDeCapturaRepository estados;
    private final Clock reloj;

    public ServicioDeCamaras(
            CamaraRepository camaras,
            PcRepository pcs,
            ConsultarEstadoDeCaptura estadoDeCaptura,
            EstadoDeCapturaRepository estados,
            Clock reloj) {
        this.camaras = camaras;
        this.pcs = pcs;
        this.estadoDeCaptura = estadoDeCaptura;
        this.estados = estados;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CamaraDetectada> buscarDetectada(UUID camaraId) {
        return camaras.buscarAunqueRetirada(camaraId)
                .filter(camara ->
                        camara.getPcId() != null && pcs.buscar(camara.getPcId()).isPresent())
                .filter(camara -> camara.enUso() || camara.getDispositivoId() != null)
                .map(camara -> {
                    Instant ahora = reloj.instant();
                    boolean consentimiento = estados.actual()
                            .map(EstadoDeCaptura::isConsentimientoVigente)
                            .orElse(false);
                    boolean enUso = camara.enUso();
                    return new CamaraDetectada(
                            camara.getId(),
                            camara.getPcId(),
                            camara.getDispositivoId(),
                            camara.getNombreDispositivo(),
                            enUso,
                            enUso
                                    ? camara.getEstadoConexion() == EstadoConexion.EN_LINEA
                                    : camara.isDetectadaConectada(),
                            consentimiento && !(enUso && camara.estaPausada(ahora)),
                            enUso && camara.estaPausada(ahora) ? camara.getPausadaHasta() : null);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CamaraDelHogar> buscar(UUID camaraId) {
        return camaras.buscar(camaraId)
                .map(camara -> new CamaraDelHogar(
                        camara.getId(),
                        camara.getNombreHabitacion(),
                        estadoDeCaptura.ejecutar(camaraId).capturaPermitida()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EstadoDeCamara> estado(UUID camaraId) {
        return camaras.buscar(camaraId).map(ServicioDeCamaras::estado);
    }

    @Override
    @Transactional
    public Optional<EstadoDeCamara> pausar(UUID camaraId, Instant hasta) {
        return camaras.buscar(camaraId).map(camara -> {
            camara.pausar(hasta);
            return estado(camaras.guardar(camara));
        });
    }

    @Override
    @Transactional
    public Optional<EstadoDeCamara> reanudar(UUID camaraId) {
        return camaras.buscar(camaraId).map(camara -> {
            camara.reanudar();
            return estado(camaras.guardar(camara));
        });
    }

    @Override
    @Transactional
    public Optional<EstadoDeCamara> finalizarPausaSiVencio(UUID camaraId, Instant ahora) {
        return camaras.buscar(camaraId)
                .filter(camara -> camara.finalizarPausaSiVencio(ahora))
                .map(camara -> estado(camaras.guardar(camara)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CamaraEnHogar> conPausaVencida(Instant ahora) {
        return camaras.conPausaVencida(ahora).stream()
                .map(c -> new CamaraEnHogar(c.camaraId(), c.hogarId()))
                .toList();
    }

    private static EstadoDeCamara estado(Camara camara) {
        return new EstadoDeCamara(
                camara.getId(),
                camara.getNombreHabitacion(),
                camara.getEstadoConexion().name(),
                camara.getUltimaSenal(),
                camara.getPausadaHasta(),
                camara.isDeteccionConfiable(),
                camara.getInstaladaEn(),
                camara.getNoConfiableDesde(),
                camara.getPcId(),
                camara.getAmbienteId(),
                camara.getNombreDispositivo());
    }

    @Override
    @Transactional
    public boolean marcarDeteccionNoConfiable(UUID camaraId, Instant desde) {
        return camaras.buscar(camaraId)
                .map(camara -> {
                    boolean cambio = camara.marcarDeteccionNoConfiable(desde);
                    camaras.guardar(camara);
                    return cambio;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void marcarDeteccionConfiable(UUID camaraId) {
        camaras.buscar(camaraId).ifPresent(camara -> {
            camara.marcarDeteccionConfiable();
            camaras.guardar(camara);
        });
    }
}
