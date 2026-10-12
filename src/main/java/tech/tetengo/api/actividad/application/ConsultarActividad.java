package tech.tetengo.api.actividad.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.actividad.application.port.ActividadRepository;
import tech.tetengo.api.actividad.domain.model.Actividad;
import tech.tetengo.api.actividad.domain.model.TipoDeActividad;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;

/**
 * US-33 / CA-33.2: any member, owner or invited, reads the log newest first, optionally of one PC; the
 * app groups it by day. With no entries the list is empty (CA-33.4).
 */
@Service
public class ConsultarActividad {

    private final ActividadRepository actividad;
    private final DirectorioDeUsuarios usuarios;

    public ConsultarActividad(ActividadRepository actividad, DirectorioDeUsuarios usuarios) {
        this.actividad = actividad;
        this.usuarios = usuarios;
    }

    public record Pagina(List<ActividadConsultada> elementos, long total) {}

    @Transactional(readOnly = true)
    public Pagina ejecutar(UUID pcId, TipoDeActividad tipo, Instant desde, Instant hasta, int pagina, int tamano) {
        var encontrada = actividad.buscar(new ActividadRepository.Filtro(pcId, tipo, desde, hasta), pagina, tamano);
        Map<UUID, DirectorioDeUsuarios.Usuario> nombres = usuarios.buscarTodos(encontrada.elementos().stream()
                .map(Actividad::getUsuarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return new Pagina(
                encontrada.elementos().stream()
                        .map(a -> new ActividadConsultada(
                                a,
                                a.getUsuarioId() != null && nombres.containsKey(a.getUsuarioId())
                                        ? nombres.get(a.getUsuarioId()).nombre()
                                        : null))
                        .toList(),
                encontrada.total());
    }
}
