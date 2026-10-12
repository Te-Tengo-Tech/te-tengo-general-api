package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.application.port.SubidaDeFotoRepository;
import tech.tetengo.api.hogares.domain.model.DestinoDeFoto;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.hogares.domain.model.SubidaDeFoto;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Subida;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * API contract §13: hands out a pre-signed PUT, valid 10 minutes, for a photo of the caller or, for
 * the owner only, of the older adult.
 */
@Service
public class SolicitarSubidaDeFoto {

    private final SubidaDeFotoRepository subidas;
    private final MiembroActual miembroActual;
    private final AlmacenamientoDeFotos almacenamiento;
    private final Clock reloj;

    public SolicitarSubidaDeFoto(
            SubidaDeFotoRepository subidas,
            MiembroActual miembroActual,
            AlmacenamientoDeFotos almacenamiento,
            Clock reloj) {
        this.subidas = subidas;
        this.miembroActual = miembroActual;
        this.almacenamiento = almacenamiento;
        this.reloj = reloj;
    }

    @Transactional
    public SubidaPedida ejecutar(UUID usuarioId, DestinoDeFoto destino, String tipo, long tamanoBytes) {
        UUID hogarId = null;
        if (destino == DestinoDeFoto.ADULTO_MAYOR) {
            Membresia membresia = miembroActual.de(usuarioId);
            if (!membresia.esTitular()) {
                throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
            }
            hogarId = membresia.getHogarId();
        }
        Instant ahora = reloj.instant();
        SubidaDeFoto subida =
                subidas.guardar(SubidaDeFoto.pedir(usuarioId, destino, hogarId, tipo, tamanoBytes, ahora));
        Subida firmada = almacenamiento.urlDeSubida(
                subida.getClave(), subida.getTipo(), subida.getTamanoBytes(), subida.urlExpiraEn());
        return new SubidaPedida(subida.getId(), firmada.url(), firmada.cabeceras(), subida.urlExpiraEn());
    }
}
