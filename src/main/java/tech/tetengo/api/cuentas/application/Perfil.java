package tech.tetengo.api.cuentas.application;

import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;

/** {@code Perfil} of the API contract (§13): the account and its photo, null without one. */
public record Perfil(Cuenta cuenta, Foto foto) {}
