package tech.tetengo.api.camaras.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.model.Camara;

@Service
public class ServicioDeCamaras implements CamarasDelHogar {

    private final CamaraRepository camaras;
    private final ConsultarEstadoDeCaptura estadoDeCaptura;

    public ServicioDeCamaras(CamaraRepository camaras, ConsultarEstadoDeCaptura estadoDeCaptura) {
        this.camaras = camaras;
        this.estadoDeCaptura = estadoDeCaptura;
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
                camara.isDeteccionConfiable());
    }

    @Override
    @Transactional
    public boolean marcarDeteccionNoConfiable(UUID camaraId) {
        return camaras.buscar(camaraId)
                .map(camara -> {
                    boolean cambio = camara.marcarDeteccionNoConfiable();
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
