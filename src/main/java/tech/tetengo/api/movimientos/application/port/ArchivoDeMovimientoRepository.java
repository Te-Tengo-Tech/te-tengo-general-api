package tech.tetengo.api.movimientos.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.movimientos.domain.model.ArchivoDeMovimiento;

/** The movement index of the household in context, except the native lookup for the jobs. */
public interface ArchivoDeMovimientoRepository {

    ArchivoDeMovimiento guardar(ArchivoDeMovimiento archivo);

    Optional<ArchivoDeMovimiento> buscar(UUID id);

    Optional<ArchivoDeMovimiento> deCamaraDesde(UUID camaraId, Instant desde);

    /** Files of the camera in the index that overlap {@code [desde, hasta)}, oldest first. */
    List<ArchivoDeMovimiento> disponiblesEntre(UUID camaraId, Instant desde, Instant hasta);

    /** Start of the oldest file in the index; empty without files. */
    Optional<Instant> primero();

    /** Files in the index and their total size. */
    Totales totales();

    /** Up to {@code limite} files (any state) that ended before {@code antesDe}, oldest first. */
    List<ArchivoDeMovimiento> anterioresA(Instant antesDe, int limite);

    /** Up to {@code limite} files still pending whose upload began before {@code antesDe}. */
    List<ArchivoDeMovimiento> pendientesAnterioresA(Instant antesDe, int limite);

    /** Up to {@code limite} files of the household, any state. */
    List<ArchivoDeMovimiento> algunos(int limite);

    void eliminar(List<ArchivoDeMovimiento> archivos);

    /** Households with files: a native query, only for the jobs. */
    List<UUID> hogaresConArchivos();

    record Totales(long archivos, long tamanoBytes) {}
}
