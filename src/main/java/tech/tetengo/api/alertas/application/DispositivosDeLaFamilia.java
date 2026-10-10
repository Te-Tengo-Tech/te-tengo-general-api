package tech.tetengo.api.alertas.application;

import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.shared.application.port.DispositivosDePush;

/** {@link DispositivosDePush} for {@code hogares}: active devices are the ones push notices go to. */
@Service
public class DispositivosDeLaFamilia implements DispositivosDePush {

    private final DispositivoRepository dispositivos;

    public DispositivosDeLaFamilia(DispositivoRepository dispositivos) {
        this.dispositivos = dispositivos;
    }

    @Override
    @Transactional(readOnly = true)
    public long activosDe(Collection<UUID> usuarioIds) {
        return dispositivos.activosDe(usuarioIds);
    }
}
