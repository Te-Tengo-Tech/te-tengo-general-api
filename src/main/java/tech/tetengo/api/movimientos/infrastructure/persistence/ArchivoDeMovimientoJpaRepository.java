package tech.tetengo.api.movimientos.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.movimientos.domain.model.ArchivoDeMovimiento;
import tech.tetengo.api.movimientos.domain.model.EstadoArchivo;

interface ArchivoDeMovimientoJpaRepository extends JpaRepository<ArchivoDeMovimiento, UUID> {

    Optional<ArchivoDeMovimiento> findByCamaraIdAndDesde(UUID camaraId, Instant desde);

    List<ArchivoDeMovimiento> findByCamaraIdAndEstadoAndDesdeLessThanAndHastaGreaterThanOrderByDesdeAsc(
            UUID camaraId, EstadoArchivo estado, Instant hasta, Instant desde);

    @Query("select min(a.desde) from ArchivoDeMovimiento a where a.estado = :estado")
    Optional<Instant> primero(EstadoArchivo estado);

    /** Count and bytes, the confirmed light replay files included (ADR 0011). */
    @Query("""
            select count(a),
                   coalesce(sum(a.tamanoBytes), 0L)
                   + coalesce(sum(case when a.ligeroSubidoEn is not null then a.ligeroTamanoBytes else 0L end), 0L)
            from ArchivoDeMovimiento a where a.estado = :estado""")
    List<Object[]> totales(EstadoArchivo estado);

    List<ArchivoDeMovimiento> findByHastaLessThanOrderByHastaAsc(Instant antesDe, Pageable pagina);

    List<ArchivoDeMovimiento> findByEstadoAndCreadoEnLessThan(EstadoArchivo estado, Instant antesDe, Pageable pagina);

    List<ArchivoDeMovimiento> findAllBy(Pageable pagina);

    /** Native, across households: only for the jobs. */
    @Query(value = "select distinct hogar_id from archivos_de_movimiento", nativeQuery = true)
    List<UUID> hogaresConArchivos();
}
