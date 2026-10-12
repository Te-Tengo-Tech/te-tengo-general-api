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

/** US-08: the family members linked to the household, owner first, with their profiles (§13). */
@Service
public class ListarFamiliares {

    private final MembresiaRepository membresias;
    private final MiembroActual miembroActual;
    private final DirectorioDeUsuarios usuarios;
    private final FotosDelHogar fotos;

    public ListarFamiliares(
            MembresiaRepository membresias,
            MiembroActual miembroActual,
            DirectorioDeUsuarios usuarios,
            FotosDelHogar fotos) {
        this.membresias = membresias;
        this.miembroActual = miembroActual;
        this.usuarios = usuarios;
        this.fotos = fotos;
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
                    if (u == null) {
                        return new Familiar(m.getUsuarioId(), null, null, m.getRol(), null, null, null);
                    }
                    return new Familiar(
                            m.getUsuarioId(),
                            u.nombre(),
                            u.correo(),
                            m.getRol(),
                            u.telefono(),
                            u.parentesco(),
                            fotos.de(u.fotoClave()));
                })
                .toList();
    }
}
