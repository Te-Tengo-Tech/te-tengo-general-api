package tech.tetengo.api.camaras.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.camaras.application.port.AmbienteRepository;
import tech.tetengo.api.camaras.application.port.CamaraRepository;
import tech.tetengo.api.camaras.domain.CamaraError;
import tech.tetengo.api.camaras.domain.model.Ambiente;
import tech.tetengo.api.camaras.domain.model.Camara;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-06 / CA-06.2 and US-29: the owner changes the room of a camera; later alerts use the new name.
 *
 * <ul>
 *   <li>By id ({@code ambienteId}): the camera moves to that room.
 *   <li>By name ({@code nombreHabitacion}, as before rooms existed): a room of that name is used if it
 *       exists; otherwise, when the camera is the only one in its room, the room itself is renamed
 *       (its id stays), or a new room is created.
 * </ul>
 *
 * The target room may not have another camera in use (CA-29.3, {@code 409 AMBIENTE_OCUPADO}).
 */
@Service
public class RenombrarCamara {

    private final CamaraRepository camaras;
    private final AmbienteRepository ambientes;
    private final ServicioDeAmbientes servicio;
    private final Clock reloj;

    public RenombrarCamara(
            CamaraRepository camaras, AmbienteRepository ambientes, ServicioDeAmbientes servicio, Clock reloj) {
        this.camaras = camaras;
        this.ambientes = ambientes;
        this.servicio = servicio;
        this.reloj = reloj;
    }

    @Transactional
    public Camara ejecutar(UUID camaraId, String nombre) {
        return ejecutar(camaraId, nombre, null, null);
    }

    @Transactional
    public Camara ejecutar(UUID camaraId, String nombre, UUID ambienteId, UUID usuarioId) {
        Camara camara = camaras.buscar(camaraId).orElseThrow(() -> new ErrorDeNegocio(CamaraError.NO_ENCONTRADA));
        Instant ahora = reloj.instant();
        Ambiente destino =
                ambienteId != null ? servicio.elegido(ambienteId, null) : porNombre(camara, nombre, usuarioId, ahora);
        servicio.exigirLibre(destino, camara);
        camara.asignarAmbiente(destino, usuarioId, ahora);
        return camaras.guardar(camara);
    }

    private Ambiente porNombre(Camara camara, String nombre, UUID usuarioId, Instant ahora) {
        String valido = Ambiente.valido(Camara.nombreDeHabitacion(nombre));
        var existente = ambientes.porNombre(Ambiente.normalizar(valido));
        if (existente.isPresent()) {
            if (existente.get().getId().equals(camara.getAmbienteId())) {
                renombrar(existente.get(), valido, usuarioId, ahora);
            }
            return existente.get();
        }
        if (camara.getAmbienteId() != null) {
            var actual = ambientes.buscar(camara.getAmbienteId());
            boolean soloEsta = camaras.enAmbiente(camara.getAmbienteId()).stream()
                    .allMatch(c -> c.getId().equals(camara.getId()));
            if (actual.isPresent() && soloEsta) {
                renombrar(actual.get(), valido, usuarioId, ahora);
                return actual.get();
            }
        }
        return ambientes.guardar(new Ambiente(valido));
    }

    private void renombrar(Ambiente ambiente, String nombre, UUID usuarioId, Instant ahora) {
        if (ambiente.renombrar(nombre, usuarioId, ahora) != null) {
            ambientes.guardar(ambiente);
            servicio.propagarNombre(ambiente);
        }
    }
}
