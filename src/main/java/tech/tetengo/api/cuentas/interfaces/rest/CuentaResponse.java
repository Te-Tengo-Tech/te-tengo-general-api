package tech.tetengo.api.cuentas.interfaces.rest;

import java.util.UUID;

record CuentaResponse(UUID id, String correo, String nombre) {}
