package tech.tetengo.api.hogares.interfaces.rest;

import tech.tetengo.api.hogares.application.ConsentimientoConsultado;
import tech.tetengo.api.hogares.application.HogarConsultado;
import tech.tetengo.api.hogares.application.HogarDelUsuario;
import tech.tetengo.api.hogares.domain.model.AdultoMayor;
import tech.tetengo.api.hogares.domain.model.Convivencia;

final class HogarMapper {

    private HogarMapper() {}

    static AdultoMayor aDominio(AdultoMayorRequest pedido) {
        return new AdultoMayor(
                pedido.nombre(),
                pedido.edad(),
                pedido.direccion(),
                Convivencia.valueOf(pedido.convivencia()),
                pedido.telefono());
    }

    static AdultoMayorResponse aRespuesta(AdultoMayor adultoMayor) {
        return new AdultoMayorResponse(
                adultoMayor.getNombre(),
                adultoMayor.getEdad(),
                adultoMayor.getDireccion(),
                adultoMayor.getConvivencia().name(),
                adultoMayor.getTelefono());
    }

    static HogarResponse aRespuesta(HogarConsultado consultado) {
        return new HogarResponse(
                consultado.hogar().getId(),
                aRespuesta(consultado.hogar().getAdultoMayor()),
                consultado.rol().name(),
                consultado.consentimiento().map(HogarMapper::aRespuesta).orElse(null),
                consultado.dispositivosActivos());
    }

    static ConsentimientoResponse aRespuesta(ConsentimientoConsultado consultado) {
        var c = consultado.consentimiento();
        var registradoPor = consultado.registradoPor() == null
                ? new ConsentimientoResponse.RegistradoPor(c.getRegistradoPor(), null)
                : new ConsentimientoResponse.RegistradoPor(
                        consultado.registradoPor().id(),
                        consultado.registradoPor().nombre());
        return new ConsentimientoResponse(
                c.getOtorgadoEn(), c.getOtorgadoPor(), registradoPor, c.isVistaEnVivoAceptada(), c.vigente());
    }

    static HogarDelUsuarioResponse aRespuesta(HogarDelUsuario hogar) {
        return new HogarDelUsuarioResponse(
                hogar.hogarId(), hogar.nombreAdultoMayor(), hogar.rol().name());
    }
}
