package tech.tetengo.api.actividad.application;

import tech.tetengo.api.actividad.domain.model.Actividad;

/** An entry with the member's name, read when listed. */
public record ActividadConsultada(Actividad actividad, String nombreDelUsuario) {}
