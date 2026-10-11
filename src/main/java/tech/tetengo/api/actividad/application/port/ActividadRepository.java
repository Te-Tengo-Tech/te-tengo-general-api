package tech.tetengo.api.actividad.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tech.tetengo.api.actividad.domain.model.Actividad;
import tech.tetengo.api.actividad.domain.model.TipoDeActividad;

/** The activity log of the household in context. Insert and read only. */
public interface ActividadRepository {

    Actividad registrar(Actividad actividad);

    boolean existe(String clave);

    /** CA-33.2: newest first, optionally of one PC, one type and a time range. */
    Pagina buscar(Filtro filtro, int pagina, int tamano);

    record Filtro(UUID pcId, TipoDeActividad tipo, Instant desde, Instant hasta) {}

    record Pagina(List<Actividad> elementos, long total) {}
}
