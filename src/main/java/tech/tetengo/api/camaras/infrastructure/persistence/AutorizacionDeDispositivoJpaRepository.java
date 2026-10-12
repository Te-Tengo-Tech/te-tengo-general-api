package tech.tetengo.api.camaras.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import tech.tetengo.api.camaras.domain.model.AutorizacionDeDispositivo;
import tech.tetengo.api.camaras.domain.model.EstadoAutorizacion;

interface AutorizacionDeDispositivoJpaRepository extends JpaRepository<AutorizacionDeDispositivo, UUID> {

    Optional<AutorizacionDeDispositivo> findByCodigoDispositivoHash(String huella);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutorizacionDeDispositivo a where a.codigoDispositivoHash = :huella")
    Optional<AutorizacionDeDispositivo> findByCodigoDispositivoHashForUpdate(String huella);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutorizacionDeDispositivo a where a.codigoUsuarioHash = :huella order by a.creadoEn desc")
    List<AutorizacionDeDispositivo> findByCodigoUsuarioHashForUpdate(String huella, Pageable pagina);

    boolean existsByCodigoUsuarioHashAndEstadoAndExpiraEnAfter(String huella, EstadoAutorizacion estado, Instant ahora);

    @Modifying
    @Query("delete from AutorizacionDeDispositivo a where a.expiraEn < :limite")
    int deleteByExpiraEnBefore(Instant limite);
}
