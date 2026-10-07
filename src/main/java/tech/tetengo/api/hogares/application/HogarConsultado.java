package tech.tetengo.api.hogares.application;

import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.shared.domain.model.Rol;

/** The household of the request as seen by one of its members. */
public record HogarConsultado(Hogar hogar, Rol rol) {}
