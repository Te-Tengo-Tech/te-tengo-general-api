package tech.tetengo.api.hogares.application;

import tech.tetengo.api.hogares.domain.model.AdultoMayor;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;

/** {@code adultoMayor} of the API contract: the profile and its photo (§13), null without one. */
public record AdultoMayorConFoto(AdultoMayor adultoMayor, Foto foto) {}
