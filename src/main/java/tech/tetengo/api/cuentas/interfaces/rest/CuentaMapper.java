package tech.tetengo.api.cuentas.interfaces.rest;

import tech.tetengo.api.cuentas.domain.model.Cuenta;

final class CuentaMapper {

    private CuentaMapper() {}

    static CuentaResponse aRespuesta(Cuenta cuenta) {
        return new CuentaResponse(cuenta.getId(), cuenta.getCorreo(), cuenta.getNombre());
    }
}
