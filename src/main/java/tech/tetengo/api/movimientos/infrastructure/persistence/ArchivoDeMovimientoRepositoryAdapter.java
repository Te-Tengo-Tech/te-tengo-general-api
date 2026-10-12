package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.domain.model.ArchivoDeMovimiento;
import tech.tetengo.api.movimientos.domain.model.EstadoArchivo;

@Repository
class ArchivoDeMovimientoRepositoryAdapter implements ArchivoDeMovimientoRepository {

    private final ArchivoDeMovimientoJpaRepository jpa;

    ArchivoDeMovimientoRepositoryAdapter(ArchivoDeMovimientoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ArchivoDeMovimiento guardar(ArchivoDeMovimiento archivo) {
        return jpa.save(archivo);
    }

    @Override
    public Optional<ArchivoDeMovimiento> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<ArchivoDeMovimiento> deCamaraDesde(UUID camaraId, Instant desde) {
        return jpa.findByCamaraIdAndDesde(camaraId, desde);
    }

    @Override
    public List<ArchivoDeMovimiento> disponiblesEntre(UUID camaraId, Instant desde, Instant hasta) {
        return jpa.findByCamaraIdAndEstadoAndDesdeLessThanAndHastaGreaterThanOrderByDesdeAsc(
                camaraId, EstadoArchivo.DISPONIBLE, hasta, desde);
    }

    @Override
    public Optional<Instant> primero() {
        return jpa.primero(EstadoArchivo.DISPONIBLE);
    }

    @Override
    public Totales totales() {
        Object[] fila = jpa.totales(EstadoArchivo.DISPONIBLE).getFirst();
        return new Totales(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
    }

    @Override
    public List<ArchivoDeMovimiento> anterioresA(Instant antesDe, int limite) {
        return jpa.findByHastaLessThanOrderByHastaAsc(antesDe, PageRequest.of(0, limite));
    }

    @Override
    public List<ArchivoDeMovimiento> pendientesAnterioresA(Instant antesDe, int limite) {
        return jpa.findByEstadoAndCreadoEnLessThan(EstadoArchivo.PENDIENTE, antesDe, PageRequest.of(0, limite));
    }

    @Override
    public List<ArchivoDeMovimiento> algunos(int limite) {
        return jpa.findAllBy(PageRequest.of(0, limite));
    }

    @Override
    public void eliminar(List<ArchivoDeMovimiento> archivos) {
        jpa.deleteAll(archivos);
        jpa.flush();
    }

    @Override
    public List<UUID> hogaresConArchivos() {
        return jpa.hogaresConArchivos();
    }
}
