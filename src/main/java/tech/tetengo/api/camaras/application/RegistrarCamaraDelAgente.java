package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.InstalacionRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Ambiente;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.Instalacion;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.Rol;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.ClaimsDelToken;
import tech.tetengo.api.shared.infrastructure.security.EmisorDeTokens;
import tech.tetengo.api.shared.infrastructure.security.Secretos;
import tech.tetengo.api.shared.infrastructure.security.TokenEmitido;

/**
 * US-06 / CA-06.1: the agent registers its camera when it starts, with the installation credential.
 * The first registration creates the camera with the room name set at installation; later ones
 * return the same camera and keep a name the family may have changed (CA-06.2). The agent gets a
 * per-camera token ({@code hogar_id}, {@code camara_id}, {@code rol = AGENTE}) that only works for its
 * household.
 *
 * <p>Since PCs exist (US-30), an installation runs on a PC of origin {@code INSTALACION}, created by
 * its first registration (or by migration {@code V24}); once that PC is unlinked (US-31) the
 * credential is rejected like an unknown one.
 */
@Service
public class RegistrarCamaraDelAgente {

    private final InstalacionRepository instalaciones;
    private final CamaraRepository camaras;
    private final EjecutorEnHogar enHogar;
    private final EmisorDeTokens emisor;
    private final PropiedadesDelAgente propiedades;
    private final PcRepository pcs;
    private final ServicioDeAmbientes ambientes;
    private final Clock reloj;

    public RegistrarCamaraDelAgente(
            InstalacionRepository instalaciones,
            CamaraRepository camaras,
            EjecutorEnHogar enHogar,
            EmisorDeTokens emisor,
            PropiedadesDelAgente propiedades,
            PcRepository pcs,
            ServicioDeAmbientes ambientes,
            Clock reloj) {
        this.instalaciones = instalaciones;
        this.camaras = camaras;
        this.enHogar = enHogar;
        this.emisor = emisor;
        this.propiedades = propiedades;
        this.pcs = pcs;
        this.ambientes = ambientes;
        this.reloj = reloj;
    }

    /**
     * The installation's PC, created at its first registration. An unlinked PC (US-31) makes the
     * credential invalid.
     */
    private Pc pcDe(Instalacion instalacion) {
        if (instalacion.getPcId() == null) {
            Pc pc = pcs.guardar(Pc.deInstalacion(reloj.instant()));
            instalacion.asignarPc(pc.getId());
            return pc;
        }
        return pcs.buscar(instalacion.getPcId()).orElseThrow(() -> new ErrorDeNegocio(CamaraError.CREDENCIAL_INVALIDA));
    }

    /** Not transactional: the camera is written inside the household found by the credential. */
    public RegistroDeCamara ejecutar(String credencial, String nombreHabitacion) {
        String huella = Secretos.huella(credencial);
        UUID hogarId = instalaciones
                .buscarPorCredencial(huella)
                .map(Instalacion::getHogarId)
                .orElseThrow(() -> new ErrorDeNegocio(CamaraError.CREDENCIAL_INVALIDA));
        return enHogar.obtener(hogarId, () -> {
            // Locked: an agent's threads may register at the same time; only the first creates the camera.
            Instalacion instalacion =
                    instalaciones.buscarPorCredencialParaActualizar(huella).orElseThrow();
            Pc pc = pcDe(instalacion);
            Camara camara = instalacion.getCamaraId() == null
                    ? null
                    : camaras.buscar(instalacion.getCamaraId()).orElse(null);
            if (camara == null) {
                // The installation's room, shared if another camera has it: the team configured it.
                Ambiente ambiente = ambientes.buscarOCrear(Camara.nombreDeHabitacion(nombreHabitacion));
                Camara nueva = new Camara(ambiente.getNombre(), pc.getId());
                nueva.ubicarEn(ambiente);
                camara = camaras.guardar(nueva);
                instalacion.asignarCamara(camara.getId());
            } else if (camara.getPcId() == null) {
                camara.asignarPc(pc.getId());
                camaras.guardar(camara);
            }
            instalaciones.guardar(instalacion);
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put(ClaimsDelToken.HOGAR, hogarId);
            claims.put(ClaimsDelToken.CAMARA, camara.getId());
            claims.put(ClaimsDelToken.ROL, Rol.AGENTE);
            TokenEmitido token = emisor.emitir(camara.getId().toString(), claims, propiedades.vigenciaToken());
            return new RegistroDeCamara(
                    camara.getId(), hogarId, token.valor(), token.expiraEn(), camara.getNombreHabitacion());
        });
    }
}
