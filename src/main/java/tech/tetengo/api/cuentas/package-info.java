/**
 * Accounts: registration, sign-in with lockout after 5 failures, refresh and sign-out, and password
 * recovery; issues the RS256 JWTs with the {@code hogar_id} and {@code rol} claims. Stories US-01 to
 * US-03.
 *
 * <p>Public API: {@code ServicioDeSesiones}, {@code Sesion}, {@code AltaDeCuentas},
 * {@code DirectorioDeUsuarios}; {@code MembresiasDeUsuario} is implemented by {@code hogares}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cuentas y acceso")
package tech.tetengo.api.cuentas;
