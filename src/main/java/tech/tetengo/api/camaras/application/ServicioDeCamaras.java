package tech.tetengo.api.camaras.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.application.port.CamaraRepository;

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
