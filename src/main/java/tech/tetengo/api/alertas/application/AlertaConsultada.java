package tech.tetengo.api.alertas.application;

import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.alertas.domain.model.EstadoClip;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios.Usuario;

/** An alert as the app sees it: with who attended it (CA-19.3) and the state of its clip. */
public record AlertaConsultada(Alerta alerta, Usuario atendidaPor, EstadoClip clip) {}
