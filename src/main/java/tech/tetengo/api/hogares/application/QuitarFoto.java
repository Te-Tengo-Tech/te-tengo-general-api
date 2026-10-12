package tech.tetengo.api.hogares.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.FotosDeCuentas;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.DestinoDeFoto;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** API contract §13: «Quitar la foto». Removing a missing photo also succeeds. */
@Service
public class QuitarFoto {

    private final HogarRepository hogares;
    private final MiembroActual miembroActual;
    private final FotosDeCuentas fotosDeCuentas;
    private final AlmacenamientoDeFotos almacenamiento;
    private final Clock reloj;

    public QuitarFoto(
            HogarRepository hogares,
            MiembroActual miembroActual,
            FotosDeCuentas fotosDeCuentas,
            AlmacenamientoDeFotos almacenamiento,
            Clock reloj) {
        this.hogares = hogares;
        this.miembroActual = miembroActual;
        this.fotosDeCuentas = fotosDeCuentas;
        this.almacenamiento = almacenamiento;
        this.reloj = reloj;
    }

    @Transactional
    public void ejecutar(UUID usuarioId, DestinoDeFoto destino) {
        Optional<String> anterior;
        if (destino == DestinoDeFoto.YO) {
            anterior = fotosDeCuentas.quitar(usuarioId);
        } else {
            Membresia membresia = miembroActual.de(usuarioId);
            if (!membresia.esTitular()) {
                throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
            }
            Hogar hogar = hogares.buscar(membresia.getHogarId())
                    .orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
            anterior = hogar.quitarFotoDelAdultoMayor(usuarioId, reloj.instant());
            hogares.guardar(hogar);
        }
        anterior.ifPresent(clave -> TrasConfirmar.ejecutar(() -> almacenamiento.eliminar(clave)));
    }
}
