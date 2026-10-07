package tech.tetengo.api.hogares.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios.Usuario;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.model.Membresia;

/** US-08: the family members linked to the household, owner first. */
@Service
public class ListarFamiliares {

    private final MembresiaRepository membresias;
    private final MiembroActual miembroActual;
    private final DirectorioDeUsuarios usuarios;

    public ListarFamiliares(
            MembresiaRepository membresias, MiembroActual miembroActual, DirectorioDeUsuarios usuarios) {
        this.membresias = membresias;
        this.miembroActual = miembroActual;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<Familiar> ejecutar(UUID usuarioId) {
        UUID hogarId = miembroActual.de(usuarioId).getHogarId();
        List<Membresia> miembros = membresias.delHogar(hogarId);
        Map<UUID, Usuario> porId = usuarios.buscarTodos(
                miembros.stream().map(Membresia::getUsuarioId).toList());
        return miembros.stream()
                .map(m -> {
                    Usuario u = porId.get(m.getUsuarioId());
                    return new Familiar(
                            m.getUsuarioId(), u == null ? null : u.nombre(), u == null ? null : u.correo(), m.getRol());
                })
                .toList();
    }
}
