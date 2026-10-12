package tech.tetengo.api.cuentas.interfaces.rest;

import java.util.UUID;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos.Foto;

/** {@code Perfil} of the API contract (§13). */
record PerfilResponse(UUID id, String nombre, String correo, String telefono, String parentesco, Foto foto) {}
