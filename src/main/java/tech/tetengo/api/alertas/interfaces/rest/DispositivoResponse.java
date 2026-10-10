package tech.tetengo.api.alertas.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.alertas.domain.model.Dispositivo;

/** {@code Dispositivo} of the API contract (§7). The push token is never sent back. */
record DispositivoResponse(UUID id, String plataforma, boolean activo, Instant vistoEn, Instant desactivadoEn) {

    static DispositivoResponse de(Dispositivo d) {
        return new DispositivoResponse(
                d.getId(), d.getPlataforma().name(), d.isActivo(), d.getVistoEn(), d.getDesactivadoEn());
    }
}
