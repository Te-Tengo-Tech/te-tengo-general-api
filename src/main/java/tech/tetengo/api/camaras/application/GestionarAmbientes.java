package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.AmbienteRepository;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Ambiente;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-29: the household's rooms. Any member lists them; the owner creates, renames and deletes them.
 * A renamed room renames its cameras, so later alerts use the new name (CA-29.2, CA-06.2).
 */
@Service
public class GestionarAmbientes {

    private final AmbienteRepository ambientes;
    private final CamaraRepository camaras;
    private final ServicioDeAmbientes servicio;
    private final Clock reloj;

    public GestionarAmbientes(
            AmbienteRepository ambientes, CamaraRepository camaras, ServicioDeAmbientes servicio, Clock reloj) {
        this.ambientes = ambientes;
        this.camaras = camaras;
        this.servicio = servicio;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<AmbienteConsultado> listar() {
        return servicio.listar();
    }

    @Transactional
    public AmbienteConsultado crear(String nombre) {
        exigirNombreLibre(nombre, null);
        Ambiente ambiente = ambientes.guardar(new Ambiente(nombre));
        return new AmbienteConsultado(ambiente.getId(), ambiente.getNombre(), List.of());
    }

    @Transactional
    public AmbienteConsultado renombrar(UUID ambienteId, String nombre, UUID usuarioId) {
        Ambiente ambiente = buscar(ambienteId);
        exigirNombreLibre(nombre, ambienteId);
        if (ambiente.renombrar(nombre, usuarioId, reloj.instant()) != null) {
            ambientes.guardar(ambiente);
            servicio.propagarNombre(ambiente);
        }
        return servicio.listar().stream()
                .filter(a -> a.id().equals(ambienteId))
                .findFirst()
                .orElseThrow();
    }

    /** A room with a camera in use cannot be deleted. */
    @Transactional
    public void eliminar(UUID ambienteId) {
        Ambiente ambiente = buscar(ambienteId);
        if (!camaras.enAmbiente(ambienteId).isEmpty()) {
            throw new ErrorDeNegocio(CamaraError.AMBIENTE_CON_CAMARA);
        }
        ambiente.eliminar(reloj.instant());
        ambientes.guardar(ambiente);
    }

    private Ambiente buscar(UUID ambienteId) {
        return ambientes.buscar(ambienteId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.AMBIENTE_NO_ENCONTRADO));
    }

    private void exigirNombreLibre(String nombre, UUID salvo) {
        ambientes
                .porNombre(Ambiente.normalizar(nombre))
                .filter(otro -> !otro.getId().equals(salvo))
                .ifPresent(otro -> {
                    throw new ErrorDeNegocio(CamaraError.AMBIENTE_YA_EXISTE);
                });
    }
}
