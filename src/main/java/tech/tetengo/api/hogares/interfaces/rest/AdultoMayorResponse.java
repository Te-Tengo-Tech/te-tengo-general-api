package tech.tetengo.api.hogares.interfaces.rest;

import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;

/** {@code adultoMayor} of the API contract; {@code foto} null without one (§13). */
record AdultoMayorResponse(
        String nombre, Integer edad, String direccion, String convivencia, String telefono, Foto foto) {}
