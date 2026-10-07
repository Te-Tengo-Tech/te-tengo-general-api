package tech.tetengo.api.hogares.application;

import java.util.Optional;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.shared.domain.model.Rol;

/** The household of the request as seen by one of its members, with its latest consent. */
public record HogarConsultado(Hogar hogar, Rol rol, Optional<ConsentimientoConsultado> consentimiento) {}
