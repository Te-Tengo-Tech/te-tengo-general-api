package tech.tetengo.api.hogares.interfaces.rest;

import tech.tetengo.api.hogares.application.HogarConsultado;
import tech.tetengo.api.hogares.application.HogarDelUsuario;
import tech.tetengo.api.hogares.domain.model.AdultoMayor;
import tech.tetengo.api.hogares.domain.model.Convivencia;

final class HogarMapper {

    private HogarMapper() {}

    static AdultoMayor aDominio(AdultoMayorRequest pedido) {
        return new AdultoMayor(pedido.nombre(), pedido.direccion(), Convivencia.valueOf(pedido.convivencia()));
    }

    static AdultoMayorResponse aRespuesta(AdultoMayor adultoMayor) {
        return new AdultoMayorResponse(
                adultoMayor.getNombre(),
                adultoMayor.getDireccion(),
                adultoMayor.getConvivencia().name());
    }

    static HogarResponse aRespuesta(HogarConsultado consultado) {
        return new HogarResponse(
                consultado.hogar().getId(),
                aRespuesta(consultado.hogar().getAdultoMayor()),
                consultado.rol().name(),
                null);
    }

    static HogarDelUsuarioResponse aRespuesta(HogarDelUsuario hogar) {
        return new HogarDelUsuarioResponse(
                hogar.hogarId(), hogar.nombreAdultoMayor(), hogar.rol().name());
    }
}
