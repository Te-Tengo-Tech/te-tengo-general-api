package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.FotosDeCuentas;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.application.port.NormalizadorDeFotos;
import tech.tetengo.api.hogares.application.port.SubidaDeFotoRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.DestinoDeFoto;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.hogares.domain.model.SubidaDeFoto;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.infrastructure.multitenancy.HogarActual;

/**
 * API contract §13: reads the uploaded file, checks that it really is an image, stores it as a square
 * JPEG of 512 × 512 at most without metadata, and replaces the photo of the caller or of the older
 * adult. The upload and the replaced photo are deleted once the change commits.
 */
@Service
public class ConfirmarSubidaDeFoto {

    private final SubidaDeFotoRepository subidas;
    private final HogarRepository hogares;
    private final MiembroActual miembroActual;
    private final FotosDeCuentas fotosDeCuentas;
    private final AlmacenamientoDeFotos almacenamiento;
    private final NormalizadorDeFotos normalizador;
    private final Clock reloj;

    public ConfirmarSubidaDeFoto(
            SubidaDeFotoRepository subidas,
            HogarRepository hogares,
            MiembroActual miembroActual,
            FotosDeCuentas fotosDeCuentas,
            AlmacenamientoDeFotos almacenamiento,
            NormalizadorDeFotos normalizador,
            Clock reloj) {
        this.subidas = subidas;
        this.hogares = hogares;
        this.miembroActual = miembroActual;
        this.fotosDeCuentas = fotosDeCuentas;
        this.almacenamiento = almacenamiento;
        this.normalizador = normalizador;
        this.reloj = reloj;
    }

    @Transactional
    public Foto ejecutar(UUID usuarioId, UUID subidaId) {
        Instant ahora = reloj.instant();
        SubidaDeFoto subida =
                subidas.buscar(subidaId).orElseThrow(() -> new ErrorDeNegocio(HogarError.SUBIDA_NO_ENCONTRADA));
        subida.verificarConfirmacion(usuarioId, HogarActual.obtener().orElse(null), ahora);
        Hogar hogar = null;
        if (subida.getDestino() == DestinoDeFoto.ADULTO_MAYOR) {
            Membresia membresia = miembroActual.de(usuarioId);
            if (!membresia.esTitular()) {
                throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
            }
            hogar = hogares.buscar(membresia.getHogarId())
                    .orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
        }

        long tamano = almacenamiento
                .tamano(subida.getClave())
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.SUBIDA_NO_ENCONTRADA));
        if (tamano > SubidaDeFoto.TAMANO_MAXIMO) {
            throw new ErrorDeNegocio(HogarError.FOTO_MUY_GRANDE);
        }
        byte[] original = almacenamiento
                .leer(subida.getClave())
                .orElseThrow(() -> new ErrorDeNegocio(HogarError.SUBIDA_NO_ENCONTRADA));
        if (original.length > SubidaDeFoto.TAMANO_MAXIMO) {
            throw new ErrorDeNegocio(HogarError.FOTO_MUY_GRANDE);
        }
        byte[] foto = normalizador.normalizar(original);

        String clave;
        Optional<String> anterior;
        if (hogar == null) {
            clave = "fotos/usuarios/%s/%s.jpg".formatted(usuarioId, subida.getId());
            almacenamiento.guardar(clave, foto, "image/jpeg");
            anterior = fotosDeCuentas.cambiar(usuarioId, clave);
        } else {
            clave = "fotos/hogares/%s/adulto-mayor/%s.jpg".formatted(hogar.getId(), subida.getId());
            almacenamiento.guardar(clave, foto, "image/jpeg");
            anterior = hogar.cambiarFotoDelAdultoMayor(clave, usuarioId, ahora);
            hogares.guardar(hogar);
        }
        subidas.eliminar(subida);
        String claveDeLaSubida = subida.getClave();
        TrasConfirmar.ejecutar(() -> {
            almacenamiento.eliminar(claveDeLaSubida);
            anterior.ifPresent(almacenamiento::eliminar);
        });
        return almacenamiento.foto(clave, ahora);
    }
}
