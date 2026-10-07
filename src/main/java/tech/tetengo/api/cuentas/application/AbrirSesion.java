package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.ServicioDeSesiones;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.application.port.SesionRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.cuentas.domain.model.SesionDeUsuario;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;
import tech.tetengo.api.shared.infrastructure.security.EmisorDeTokens;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tech.tetengo.api.shared.infrastructure.security.TokenEmitido;

/**
 * US-02: opens sessions and issues their tokens: an RS256 access token with the claims {@code sub},
 * {@code hogar_id}, {@code rol} and {@code sid}, and a persisted refresh token.
 */
@Service
public class AbrirSesion implements ServicioDeSesiones {

    private final CuentaRepository cuentas;
    private final SesionRepository sesiones;
    private final EmisorDeTokens emisor;
    private final PropiedadesDeSesion propiedades;
    private final Clock reloj;

    public AbrirSesion(
            CuentaRepository cuentas,
            SesionRepository sesiones,
            EmisorDeTokens emisor,
            PropiedadesDeSesion propiedades,
            Clock reloj) {
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.emisor = emisor;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Sesion abrir(UUID usuarioId, UUID hogarId, Rol rol, UUID sesionQueReemplaza) {
        Instant ahora = reloj.instant();
        Cuenta cuenta = cuentas.buscar(usuarioId).orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA));
        if (sesionQueReemplaza != null) {
            sesiones.buscar(sesionQueReemplaza)
                    .filter(anterior -> anterior.getUsuarioId().equals(usuarioId))
                    .ifPresent(anterior -> anterior.cerrar(ahora));
        }
        String tokenRefresco = Secretos.generar();
        SesionDeUsuario sesion = sesiones.guardar(new SesionDeUsuario(
                usuarioId, hogarId, rol, Secretos.huella(tokenRefresco), ahora.plus(propiedades.vigenciaRefresco())));
        return emitir(cuenta, sesion, tokenRefresco);
    }

    /** Issues the access token of an open session together with its (new) refresh token. */
    Sesion emitir(Cuenta cuenta, SesionDeUsuario sesion, String tokenRefresco) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(ClaimsDelToken.SESION, sesion.getId());
        claims.put(ClaimsDelToken.HOGAR, sesion.getHogarId());
        claims.put(ClaimsDelToken.ROL, sesion.getRol());
        TokenEmitido acceso = emisor.emitir(cuenta.getId().toString(), claims, propiedades.vigenciaAcceso());
        return new Sesion(
                acceso.valor(),
                tokenRefresco,
                acceso.expiraEn(),
                new Sesion.Usuario(cuenta.getId(), cuenta.getNombre(), cuenta.getCorreo()),
                sesion.getHogarId(),
                sesion.getRol());
    }

    Instant nuevaExpiracionDelRefresco() {
        return reloj.instant().plus(propiedades.vigenciaRefresco());
    }
}
