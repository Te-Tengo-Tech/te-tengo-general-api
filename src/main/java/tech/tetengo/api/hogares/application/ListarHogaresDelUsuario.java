package tech.tetengo.api.hogares.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.hogares.domain.model.Membresia;

/** The households the user belongs to, e.g. someone caring for both parents in two homes. */
@Service
public class ListarHogaresDelUsuario {

    private final MembresiaRepository membresias;
    private final HogarRepository hogares;

    public ListarHogaresDelUsuario(MembresiaRepository membresias, HogarRepository hogares) {
        this.membresias = membresias;
        this.hogares = hogares;
    }

    @Transactional(readOnly = true)
    public List<HogarDelUsuario> ejecutar(UUID usuarioId) {
        List<Membresia> propias = membresias.delUsuario(usuarioId);
        Map<UUID, Hogar> porId = hogares
                .buscarTodos(propias.stream().map(Membresia::getHogarId).toList())
                .stream()
                .collect(Collectors.toMap(Hogar::getId, Function.identity()));
        return propias.stream()
                .filter(m -> porId.containsKey(m.getHogarId()))
                .map(m -> new HogarDelUsuario(
                        m.getHogarId(),
                        porId.get(m.getHogarId()).getAdultoMayor().getNombre(),
                        m.getRol()))
                .toList();
    }
}
