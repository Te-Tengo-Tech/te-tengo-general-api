package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.application.port.PcRepository;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.camaras.domain.model.EstadoConexion;
import tech.tetengo.api.camaras.domain.model.Pc;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;

/**
 * US-30 / CA-30.2: any member, owner or invited, sees each PC of the household with its status and
 * version and, under it, its cameras with their room and status. A PC that stops connecting shows as
 * offline with its cameras (CA-30.3), while the other PCs keep detecting.
 */
@Service
public class ListarPcs {

    private final PcRepository pcs;
    private final CamaraRepository camaras;
    private final DirectorioDeUsuarios usuarios;
    private final PropiedadesDelAgente agente;
    private final Clock reloj;

    public ListarPcs(
            PcRepository pcs,
            CamaraRepository camaras,
            DirectorioDeUsuarios usuarios,
            PropiedadesDelAgente agente,
            Clock reloj) {
        this.pcs = pcs;
        this.camaras = camaras;
        this.usuarios = usuarios;
        this.agente = agente;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<PcConsultada> ejecutar() {
        List<Pc> vinculadas = pcs.vinculadas();
        Map<UUID, DirectorioDeUsuarios.Usuario> nombres = usuarios.buscarTodos(vinculadas.stream()
                .map(Pc::getVinculadaPor)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return vinculadas.stream()
                .map(pc -> consultada(pc, nombres.get(pc.getVinculadaPor())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PcConsultada consultada(Pc pc) {
        return consultada(
                pc,
                pc.getVinculadaPor() == null
                        ? null
                        : usuarios.buscar(pc.getVinculadaPor()).orElse(null));
    }

    private PcConsultada consultada(Pc pc, DirectorioDeUsuarios.Usuario vinculadaPor) {
        List<Camara> suyas = camaras.deLaPc(pc.getId());
        Instant ultimaSenal = Stream.concat(
                        Stream.of(pc.getUltimaSenal()), suyas.stream().map(Camara::getUltimaSenal))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant limite = reloj.instant().minus(agente.esperaSinSenal());
        boolean enLinea = suyas.stream().anyMatch(c -> c.getEstadoConexion() == EstadoConexion.EN_LINEA)
                || (pc.getUltimaSenal() != null && pc.getUltimaSenal().isAfter(limite));
        int sinUsar = (int) camaras.detectadasDeLaPc(pc.getId()).stream()
                .filter(camara -> !camara.enUso())
                .count();
        return new PcConsultada(
                pc, suyas, enLinea, ultimaSenal, vinculadaPor == null ? null : vinculadaPor.nombre(), sinUsar);
    }
}
