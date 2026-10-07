package tech.tetengo.api.monitoreo.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.monitoreo.application.port.AccesoVistaEnVivoRepository;
import tech.tetengo.api.shared.infrastructure.multitenancy.EjecutorEnHogar;
import tech.tetengo.api.shared.infrastructure.security.Secretos;

/**
 * The stream side of a live view session: the app connects with the one-time token of its URL, and
 * the session closes when the connection ends.
 */
@Service
public class ConexionDeTransmision {

    private final AccesoVistaEnVivoRepository accesos;
    private final EjecutorEnHogar enHogar;
    private final Clock reloj;

    public ConexionDeTransmision(AccesoVistaEnVivoRepository accesos, EjecutorEnHogar enHogar, Clock reloj) {
        this.accesos = accesos;
        this.enHogar = enHogar;
        this.reloj = reloj;
    }

    /** Validates the token for that session and marks it connected; returns its camera. */
    public Optional<Espectador> conectar(UUID sesionId, String token) {
        return accesos.porToken(Secretos.huella(token))
                .filter(s -> s.sesionId().equals(sesionId))
                .flatMap(s -> enHogar.obtener(
                        s.hogarId(),
                        () -> accesos.buscar(s.sesionId())
                                .filter(acceso -> acceso.conectar(reloj.instant()))
                                .map(acceso -> new Espectador(acceso.getId(), s.hogarId(), acceso.getCamaraId()))));
    }

    /** CA-24.1: the stream ended (the app closed or lost the connection). */
    public void desconectar(Espectador espectador) {
        enHogar.ejecutar(
                espectador.hogarId(),
                () -> accesos.buscar(espectador.sesionId()).ifPresent(acceso -> acceso.cerrar(reloj.instant())));
    }

    public record Espectador(UUID sesionId, UUID hogarId, UUID camaraId) {}
}
