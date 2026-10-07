package tech.tetengo.api.alertas.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.alertas.application.port.AlertaRepository;
import tech.tetengo.api.alertas.application.port.AlertaRepository.FiltroDeAlertas;
import tech.tetengo.api.alertas.application.port.AlertaRepository.Pagina;
import tech.tetengo.api.alertas.domain.AlertaError;
import tech.tetengo.api.alertas.domain.model.Alerta;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios.Usuario;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-25 and US-16: the household's alerts. Active alerts are there when the app opens even if their
 * push failed (CA-16.4); the history lists date, time, room, type and state (CA-25.1), can be
 * filtered (CA-25.2) and may be empty (CA-25.3).
 */
@Service
public class ConsultarAlertas {

    private final AlertaRepository alertas;
    private final EstadoDeClips estadoDeClips;
    private final DirectorioDeUsuarios usuarios;

    public ConsultarAlertas(AlertaRepository alertas, EstadoDeClips estadoDeClips, DirectorioDeUsuarios usuarios) {
        this.alertas = alertas;
        this.estadoDeClips = estadoDeClips;
        this.usuarios = usuarios;
    }

    /** Not read-only: confirming an uploaded clip is remembered on the alert. */
    @Transactional
    public Pagina<AlertaConsultada> listar(FiltroDeAlertas filtro, int pagina, int tamano) {
        Pagina<Alerta> encontradas = alertas.buscar(filtro, pagina, tamano);
        Map<UUID, Usuario> atendieron = usuarios.buscarTodos(encontradas.elementos().stream()
                .map(Alerta::getAtendidaPor)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<AlertaConsultada> elementos = encontradas.elementos().stream()
                .map(a -> new AlertaConsultada(
                        a,
                        a.getAtendidaPor() == null ? null : atendieron.get(a.getAtendidaPor()),
                        estadoDeClips.actualizar(a)))
                .toList();
        return new Pagina<>(elementos, encontradas.total());
    }

    @Transactional
    public AlertaConsultada detalle(UUID alertaId) {
        Alerta alerta =
                alertas.buscar(alertaId).orElseThrow(() -> new ErrorDeNegocio(AlertaError.ALERTA_NO_ENCONTRADA));
        return consultada(alerta);
    }

    AlertaConsultada consultada(Alerta alerta) {
        Usuario atendio = alerta.getAtendidaPor() == null
                ? null
                : usuarios.buscar(alerta.getAtendidaPor()).orElse(null);
        return new AlertaConsultada(alerta, atendio, estadoDeClips.actualizar(alerta));
    }
}
