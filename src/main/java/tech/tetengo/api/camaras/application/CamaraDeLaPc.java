package tech.tetengo.api.camaras.application;

import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.shared.infrastructure.security.TokenEmitido;

/** A camera of the linked PC with its per-camera token, when one was issued. */
public record CamaraDeLaPc(Camara camara, TokenEmitido token) {}
