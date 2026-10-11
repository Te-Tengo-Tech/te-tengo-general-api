package tech.tetengo.api.camaras.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tech.tetengo.api.camaras.application.AmbienteConsultado.CamaraEnAmbiente;
import tech.tetengo.api.camaras.application.port.AmbienteRepository;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Ambiente;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * The household's rooms as the use cases need them: found by id or by name (created when new), the
 * one-camera-per-room rule (CA-29.3; a rule of the use cases, not of the schema, so several cameras
 * per room can be allowed later), and the rooms with their cameras. Runs in the caller's transaction.
 */
@Service
public class ServicioDeAmbientes {

    private final AmbienteRepository ambientes;
    private final CamaraRepository camaras;
    private final PcRepository pcs;

    public ServicioDeAmbientes(AmbienteRepository ambientes, CamaraRepository camaras, PcRepository pcs) {
        this.ambientes = ambientes;
        this.camaras = camaras;
        this.pcs = pcs;
    }

    /** The room of that name, ignoring case; a new one if there is none (CA-29.2: «u otro nombre»). */
    public Ambiente buscarOCrear(String nombre) {
        return ambientes
                .porNombre(Ambiente.normalizar(nombre))
                .orElseGet(() -> ambientes.guardar(new Ambiente(nombre)));
    }

    /** The room chosen by id, or by name when no id is given. */
    public Ambiente elegido(UUID ambienteId, String nombre) {
        if (ambienteId != null) {
            return ambientes
                    .buscar(ambienteId)
                    .orElseThrow(() -> new ErrorDeNegocio(CamaraError.AMBIENTE_NO_ENCONTRADO));
        }
        return buscarOCrear(nombre);
    }

    /**
     * CA-29.3: the room may not have another camera in use, on this PC or another one. The error says
     * which camera has it ({@code camara: {id, pcId, pcNombre, nombreDispositivo}}).
     */
    public void exigirLibre(Ambiente ambiente, Camara camara) {
        if (camara != null && camara.getRetiradaEn() == null && ambiente.getId().equals(camara.getAmbienteId())) {
            return; // It stays where it is: rooms shared before the rule existed are left alone.
        }
        UUID camaraId = camara == null ? null : camara.getId();
        camaras.enAmbiente(ambiente.getId()).stream()
                .filter(otra -> !otra.getId().equals(camaraId))
                .findFirst()
                .ifPresent(otra -> {
                    Map<String, Object> ocupante = new LinkedHashMap<>();
                    ocupante.put("id", otra.getId());
                    ocupante.put("pcId", otra.getPcId());
                    ocupante.put(
                            "pcNombre",
                            otra.getPcId() == null
                                    ? null
                                    : pcs.buscar(otra.getPcId())
                                            .map(Pc::getNombre)
                                            .orElse(null));
                    ocupante.put("nombreDispositivo", otra.getNombreDispositivo());
                    throw new ErrorDeNegocio(
                            CamaraError.AMBIENTE_OCUPADO, Map.of("ambiente", ambiente.getNombre(), "camara", ocupante));
                });
    }

    /** The rooms, by name, each with its cameras in use. */
    public List<AmbienteConsultado> listar() {
        Map<UUID, String> nombresDePcs =
                pcs.vinculadas().stream().collect(Collectors.toMap(Pc::getId, Pc::getNombre, (a, b) -> a));
        return ambientes.listar().stream()
                .map(ambiente -> new AmbienteConsultado(
                        ambiente.getId(),
                        ambiente.getNombre(),
                        camaras.enAmbiente(ambiente.getId()).stream()
                                .map(camara -> new CamaraEnAmbiente(
                                        camara.getId(),
                                        camara.getPcId(),
                                        nombresDePcs.get(camara.getPcId()),
                                        camara.getNombreDispositivo(),
                                        camara.getEstadoConexion().name()))
                                .toList()))
                .toList();
    }

    /** The cameras of a room follow its new name. */
    public void propagarNombre(Ambiente ambiente) {
        camaras.enAmbiente(ambiente.getId()).forEach(camara -> {
            camara.seguirAmbiente(ambiente);
            camaras.guardar(camara);
        });
    }
}
