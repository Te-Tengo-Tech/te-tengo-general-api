package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 * empty list when nobody did (CA-24.3). The sessions of one «Ver todas» opening are one entry with the
 * cameras seen, from the first start to the last end (US-41, CA-41.6); cameras that could not be seen
 * opened no session, so they are not counted.
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
        List<AccesoRegistrado> entradas = new ArrayList<>();
        Map<UUID, List<AccesoVistaEnVivo>> mosaicos = new LinkedHashMap<>();
        for (AccesoVistaEnVivo acceso : registrados) {
            if (acceso.getMosaicoId() != null) {
                mosaicos.computeIfAbsent(acceso.getMosaicoId(), id -> new ArrayList<>())
                        .add(acceso);
            } else {
                entradas.add(new AccesoRegistrado(
                        acceso.getUsuarioId(),
                        nombre(nombres, acceso.getUsuarioId()),
                        acceso.getInicio(),
                        acceso.duracionSegundos(ahora),
                        acceso.desdeAlerta(),
                        acceso.getCamaraId(),
                        acceso.isVistaPrevia(),
                        List.of(acceso.getCamaraId()),
                        false));
            }
        }
        mosaicos.values().forEach(sesiones -> entradas.add(mosaico(sesiones, nombres, ahora)));
        entradas.sort(Comparator.comparing(AccesoRegistrado::inicio).reversed());
        return entradas;
    }

    /** CA-41.6: one entry for the sessions of a «Ver todas» opening. */
    private static AccesoRegistrado mosaico(
            List<AccesoVistaEnVivo> sesiones, Map<UUID, Usuario> nombres, Instant ahora) {
        List<AccesoVistaEnVivo> enOrden = sesiones.stream()
                .sorted(Comparator.comparing(AccesoVistaEnVivo::getInicio))
                .toList();
        AccesoVistaEnVivo primera = enOrden.getFirst();
        Instant inicio = primera.getInicio();
        Instant fin = enOrden.stream()
                .map(s -> s.getInicio().plusSeconds(s.duracionSegundos(ahora)))
                .max(Comparator.naturalOrder())
                .orElse(inicio);
        List<UUID> camaras =
                enOrden.stream().map(AccesoVistaEnVivo::getCamaraId).distinct().toList();
        return new AccesoRegistrado(
                primera.getUsuarioId(),
                nombre(nombres, primera.getUsuarioId()),
                inicio,
                Math.max(0, fin.getEpochSecond() - inicio.getEpochSecond()),
                enOrden.stream().anyMatch(AccesoVistaEnVivo::desdeAlerta),
                primera.getCamaraId(),
                false,
                camaras,
                true);
    }

    private static String nombre(Map<UUID, Usuario> nombres, UUID usuarioId) {
        return nombres.containsKey(usuarioId) ? nombres.get(usuarioId).nombre() : null;
    }
}
