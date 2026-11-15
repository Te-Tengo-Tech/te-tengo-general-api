package tech.tetengo.api.monitoreo.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.CamarasDelHogar;
import tech.tetengo.api.camaras.CamarasDelHogar.EstadoDeCamara;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios.Usuario;
import tech.tetengo.api.monitoreo.application.port.AccesoDenegadoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoDenegado;

/**
 * ADR 0015: the attempts to reach the household's cameras that were refused, newest first. A member who
 * asked is named; anyone else (another household, MediaMTX without a session) is not identified.
 */
@Service
public class ConsultarAccesosDenegados {

    private final AccesoDenegadoRepository denegados;
    private final CamarasDelHogar camaras;
    private final DirectorioDeUsuarios usuarios;

    public ConsultarAccesosDenegados(
            AccesoDenegadoRepository denegados, CamarasDelHogar camaras, DirectorioDeUsuarios usuarios) {
        this.denegados = denegados;
        this.camaras = camaras;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<AccesoDenegadoRegistrado> ejecutar() {
        List<AccesoDenegado> registrados = denegados.recientesPrimero();
        Map<UUID, Usuario> miembros = usuarios.buscarTodos(registrados.stream()
                .filter(AccesoDenegado::deUnMiembro)
                .map(AccesoDenegado::getUsuarioId)
                .distinct()
                .toList());
        Map<UUID, Optional<String>> habitaciones = new HashMap<>();
        return registrados.stream()
                .map(denegado -> {
                    boolean miembro = denegado.deUnMiembro();
                    UUID camara = denegado.getCamaraId();
                    String habitacion = camara == null
                            ? null
                            : habitaciones
                                    .computeIfAbsent(
                                            camara, id -> camaras.estado(id).map(EstadoDeCamara::nombreHabitacion))
                                    .orElse(null);
                    Usuario usuario = miembro ? miembros.get(denegado.getUsuarioId()) : null;
                    return new AccesoDenegadoRegistrado(
                            denegado.getOcurridoEn(),
                            denegado.getCanal().name(),
                            denegado.getMotivo().name(),
                            denegado.getAccion(),
                            denegado.getProtocolo(),
                            camara,
                            habitacion,
                            miembro,
                            miembro ? denegado.getUsuarioId() : null,
                            usuario == null ? null : usuario.nombre());
                })
                .toList();
    }
}
