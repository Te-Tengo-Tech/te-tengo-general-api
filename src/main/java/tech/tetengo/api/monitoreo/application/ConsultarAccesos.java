package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios.Usuario;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.monitoreo.domain.model.AccesoVistaEnVivo;

/**
 * US-24: who watched the camera live, when and for how long (CA-24.1), newest first (CA-24.2); an
 * empty list when nobody did (CA-24.3).
 */
@Service
public class ConsultarAccesos {

    private final AccesoVistaEnVivoRepository accesos;
    private final DirectorioDeUsuarios usuarios;
    private final Clock reloj;

    public ConsultarAccesos(AccesoVistaEnVivoRepository accesos, DirectorioDeUsuarios usuarios, Clock reloj) {
        this.accesos = accesos;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<AccesoRegistrado> ejecutar() {
        Instant ahora = reloj.instant();
        List<AccesoVistaEnVivo> registrados = accesos.recientesPrimero();
        Map<UUID, Usuario> nombres = usuarios.buscarTodos(registrados.stream()
                .map(AccesoVistaEnVivo::getUsuarioId)
                .distinct()
                .toList());
        return registrados.stream()
                .map(a -> new AccesoRegistrado(
                        a.getUsuarioId(),
                        nombres.containsKey(a.getUsuarioId())
                                ? nombres.get(a.getUsuarioId()).nombre()
                                : null,
                        a.getInicio(),
                        a.duracionSegundos(ahora),
                        a.desdeAlerta()))
                .toList();
    }
}
