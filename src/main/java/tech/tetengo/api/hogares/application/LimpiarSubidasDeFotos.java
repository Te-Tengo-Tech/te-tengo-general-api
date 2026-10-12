package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tech.tetengo.api.hogares.application.port.SubidaDeFotoRepository;
import tech.tetengo.api.hogares.domain.model.SubidaDeFoto;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;

/**
 * API contract §13: scheduled job that deletes the uploads never confirmed, 24 hours after they were
 * asked for, with their file if it arrived. Idempotent: deleting a missing object is not an error, and
 * a deleted upload is not found again.
 */
@Service
public class LimpiarSubidasDeFotos {

    private static final Logger log = LoggerFactory.getLogger(LimpiarSubidasDeFotos.class);
    private static final int LOTE = 200;

    private final SubidaDeFotoRepository subidas;
    private final AlmacenamientoDeFotos almacenamiento;
    private final Clock reloj;

    public LimpiarSubidasDeFotos(SubidaDeFotoRepository subidas, AlmacenamientoDeFotos almacenamiento, Clock reloj) {
        this.subidas = subidas;
        this.almacenamiento = almacenamiento;
        this.reloj = reloj;
    }

    /** Returns how many uploads it deleted. */
    @Scheduled(fixedDelayString = "${tetengo.fotos.limpieza-de-subidas:PT1H}")
    public int ejecutar() {
        Instant limite = reloj.instant().minus(SubidaDeFoto.CONSERVACION);
        int eliminadas = 0;
        List<SubidaDeFoto> vencidas;
        do {
            vencidas = subidas.pedidasAntesDe(limite, LOTE);
            for (SubidaDeFoto subida : vencidas) {
                try {
                    almacenamiento.eliminar(subida.getClave());
                    subidas.eliminar(subida);
                    eliminadas++;
                } catch (RuntimeException e) {
                    log.error("No se pudo eliminar la subida de foto {}", subida.getId(), e);
                    return eliminadas;
                }
            }
        } while (vencidas.size() == LOTE);
        return eliminadas;
    }
}
