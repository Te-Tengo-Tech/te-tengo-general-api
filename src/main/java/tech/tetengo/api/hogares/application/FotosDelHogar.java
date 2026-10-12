package tech.tetengo.api.hogares.application;

import java.time.Clock;
import org.springframework.stereotype.Component;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;

/** API contract §13: the photos in the household's responses, as pre-signed GETs valid 1 hour. */
@Component
public class FotosDelHogar {

    private final AlmacenamientoDeFotos almacenamiento;
    private final Clock reloj;

    public FotosDelHogar(AlmacenamientoDeFotos almacenamiento, Clock reloj) {
        this.almacenamiento = almacenamiento;
        this.reloj = reloj;
    }

    /** Null without a photo. */
    public Foto de(String clave) {
        return clave == null ? null : almacenamiento.foto(clave, reloj.instant());
    }

    public Foto delAdultoMayor(Hogar hogar) {
        return de(hogar.getFotoDelAdultoMayor());
    }
}
