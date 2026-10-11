package tech.tetengo.api.camaras.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.AutorizacionDeDispositivoRepository;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;
import tech.tetengo.api.camaras.domain.model.EstadoAutorizacion;

@Repository
class AutorizacionDeDispositivoRepositoryAdapter implements AutorizacionDeDispositivoRepository {

    private final AutorizacionDeDispositivoJpaRepository jpa;

    AutorizacionDeDispositivoRepositoryAdapter(AutorizacionDeDispositivoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AutorizacionDeDispositivo guardar(AutorizacionDeDispositivo autorizacion) {
        return jpa.save(autorizacion);
    }

    @Override
    public Optional<AutorizacionDeDispositivo> porCodigoDeDispositivoParaActualizar(String huella) {
        return jpa.findByCodigoDispositivoHashForUpdate(huella);
    }

    @Override
    public Optional<AutorizacionDeDispositivo> porCodigoDeDispositivo(String huella) {
        return jpa.findByCodigoDispositivoHash(huella);
    }

    @Override
    public Optional<AutorizacionDeDispositivo> porCodigoDeUsuarioParaActualizar(String huella) {
        return jpa.findByCodigoUsuarioHashForUpdate(huella, PageRequest.of(0, 1)).stream()
                .findFirst();
    }

    @Override
    public boolean codigoDeUsuarioEnUso(String huella, Instant ahora) {
        return jpa.existsByCodigoUsuarioHashAndEstadoAndExpiraEnAfter(huella, EstadoAutorizacion.PENDIENTE, ahora);
    }

    @Override
    public int eliminarVencidasAntesDe(Instant limite) {
        return jpa.deleteByExpiraEnBefore(limite);
    }
}
