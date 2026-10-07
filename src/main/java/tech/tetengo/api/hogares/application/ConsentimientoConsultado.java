package tech.tetengo.api.hogares.application;

import tech.tetengo.api.cuentas.DirectorioDeUsuarios.Usuario;
import tech.tetengo.api.hogares.domain.model.Consentimiento;

/** The consent with the name of the family member who registered it. */
public record ConsentimientoConsultado(Consentimiento consentimiento, Usuario registradoPor) {}
