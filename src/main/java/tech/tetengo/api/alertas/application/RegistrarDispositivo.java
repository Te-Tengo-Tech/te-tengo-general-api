package tech.tetengo.api.alertas.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.DispositivoRepository;
import tech.tetengo.api.alertas.domain.model.Dispositivo;
import tech.tetengo.api.alertas.domain.model.Plataforma;

/** API contract §7: a phone registers its push token to receive alerts (CA-16.1, CA-16.2). */
@Service
public class RegistrarDispositivo {

    private final DispositivoRepository dispositivos;

    public RegistrarDispositivo(DispositivoRepository dispositivos) {
        this.dispositivos = dispositivos;
    }

    @Transactional
    public void ejecutar(UUID usuarioId, String tokenPush, Plataforma plataforma) {
        Dispositivo dispositivo = dispositivos
                .buscarPorToken(tokenPush)
                .map(existente -> {
                    existente.asignar(usuarioId, plataforma);
                    return existente;
                })
                .orElseGet(() -> new Dispositivo(tokenPush, usuarioId, plataforma));
        dispositivos.guardar(dispositivo);
    }

    /** Only the owner of the device can remove it; anything else is a no-op. */
    @Transactional
    public void eliminar(UUID usuarioId, String tokenPush) {
        dispositivos
                .buscarPorToken(tokenPush)
                .filter(d -> d.getUsuarioId().equals(usuarioId))
                .ifPresent(dispositivos::eliminar);
    }
}
