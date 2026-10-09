package tech.tetengo.api.cuentas.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.MembresiasDeUsuario;
import tech.tetengo.api.cuentas.MembresiasDeUsuario.HogarDelUsuario;
import tech.tetengo.api.cuentas.Sesion;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.application.port.SesionRepository;
import tech.tetengo.api.cuentas.domain.CuentaError;
import tech.tetengo.api.cuentas.domain.model.Cuenta;
import tech.tetengo.api.cuentas.domain.model.SesionDeUsuario;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * US-02: trades a refresh token for new tokens and rotates it. The membership is checked again, so a
 * family member whose access was removed (CA-08.3) no longer gets tokens for that household.
 */
@Service
public class RefrescarSesion {

    private final SesionRepository sesiones;
    private final CuentaRepository cuentas;
    private final AbrirSesion abrirSesion;
    private final ObjectProvider<MembresiasDeUsuario> membresias;
    private final Clock reloj;

    public RefrescarSesion(
            SesionRepository sesiones,
            CuentaRepository cuentas,
            AbrirSesion abrirSesion,
            ObjectProvider<MembresiasDeUsuario> membresias,
            Clock reloj) {
        this.sesiones = sesiones;
        this.cuentas = cuentas;
        this.abrirSesion = abrirSesion;
        this.membresias = membresias;
        this.reloj = reloj;
    }

    @Transactional
    public Sesion ejecutar(String tokenRefresco) {
        Instant ahora = reloj.instant();
        SesionDeUsuario sesion = sesiones.buscarPorTokenRefresco(Secretos.huella(tokenRefresco))
                .filter(s -> s.vigente(ahora))
                .orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA));
        Cuenta cuenta = cuentas.buscar(sesion.getUsuarioId())
                .orElseThrow(() -> new ErrorDeNegocio(CuentaError.SESION_EXPIRADA));
        revalidarHogar(sesion);
        String nuevoToken = Secretos.generar();
        sesion.rotar(Secretos.huella(nuevoToken), abrirSesion.nuevaExpiracionDelRefresco());
        return abrirSesion.emitir(cuenta, sesion, nuevoToken);
    }

    private void revalidarHogar(SesionDeUsuario sesion) {
        MembresiasDeUsuario consulta = membresias.getIfAvailable();
        if (consulta == null || sesion.getHogarId() == null) {
            return;
        }
        Optional<Rol> rol = consulta.rolEn(sesion.getUsuarioId(), sesion.getHogarId());
        if (rol.isPresent()) {
            sesion.cambiarHogar(sesion.getHogarId(), rol.get());
            return;
        }
        Optional<HogarDelUsuario> otro = consulta.hogarPredeterminado(sesion.getUsuarioId());
        sesion.cambiarHogar(
                otro.map(HogarDelUsuario::hogarId).orElse(null),
                otro.map(HogarDelUsuario::rol).orElse(null));
    }
}
