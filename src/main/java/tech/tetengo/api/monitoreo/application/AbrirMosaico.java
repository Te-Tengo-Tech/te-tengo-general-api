package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.monitoreo.application.port.MosaicoDeVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.MosaicoDeVistaEnVivo;

/**
 * US-41: a member opens «Ver todas». Nothing is streamed yet: the app then opens one live view session
 * per visible camera with the mosaic's id, and the access log shows them as one entry (CA-41.6).
 */
@Service
public class AbrirMosaico {

    private final MosaicoDeVistaEnVivoRepository mosaicos;
    private final Clock reloj;

    public AbrirMosaico(MosaicoDeVistaEnVivoRepository mosaicos, Clock reloj) {
        this.mosaicos = mosaicos;
        this.reloj = reloj;
    }

    @Transactional
    public UUID ejecutar(UUID usuarioId) {
        return mosaicos.guardar(new MosaicoDeVistaEnVivo(usuarioId, reloj.instant()))
                .getId();
    }
}
