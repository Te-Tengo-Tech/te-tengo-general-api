package tech.tetengo.api.camaras.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.Camara;

/** Persistence port. Queries are already filtered by the current household (multi-tenancy). */
public interface CamaraRepository {

    Camara guardar(Camara camara);

    /** A camera in use: retired cameras (their PC was unlinked) are not found. */
    Optional<Camara> buscar(UUID id);

    /** The cameras in use, by room name. */
    List<Camara> listar();

    /** The cameras in use of a PC of the household. */
    List<Camara> deLaPc(UUID pcId);

    /** The cameras in use in a room (CA-29.3: at most one, for now). */
    List<Camara> enAmbiente(UUID ambienteId);

    /** The PC's camera of that webcam, in use or retired (CA-29.4: same device, same camera). */
    Optional<Camara> deLaPcPorDispositivo(UUID pcId, String dispositivoId);

    /**
     * How an agent token of the camera stands, read without the household in context (the token is
     * checked before it is bound): a native query, only for {@code ComprobadorDeAgentes}.
     */
    Optional<CamaraDeAgente> deAgente(UUID hogarId, UUID camaraId);

    /**
     * Online cameras of every household whose last heartbeat is older than {@code limite}. A native
     * query across households, only for the disconnection job (see {@code MULTITENANCY.md}).
     */
    List<CamaraDeUnHogar> enLineaSinSenalDesde(Instant limite);

    /** Paused cameras of every household whose pause is over: a native query, only for jobs. */
    List<CamaraDeUnHogar> conPausaVencida(Instant ahora);

    record CamaraDeUnHogar(UUID camaraId, UUID hogarId) {}

    /**
     * @param pcId the camera's PC, null for cameras older than PCs
     * @param pcVinculada whether that PC is still linked (true without a PC)
     */
    record CamaraDeAgente(UUID pcId, boolean retirada, boolean pcVinculada) {}
}
