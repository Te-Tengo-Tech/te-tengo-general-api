package tech.tetengo.api.cuentas;

import java.time.Instant;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.Rol;

/**
 * The {@code Sesion} of the API contract, returned by sign-in, refresh, household creation, household
 * switch and invitation acceptance. {@code hogarId} and {@code rol} are null until the account
 * creates or joins a household. {@code expiraEn} is the expiry of the access token.
 */
public record Sesion(
        String tokenAcceso, String tokenRefresco, Instant expiraEn, Usuario usuario, UUID hogarId, Rol rol) {

    public record Usuario(UUID id, String nombre, String correo) {}
}
