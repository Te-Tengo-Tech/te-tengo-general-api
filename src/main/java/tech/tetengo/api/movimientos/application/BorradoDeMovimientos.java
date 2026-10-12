package tech.tetengo.api.movimientos.application;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;
import tech.tetengo.api.movimientos.application.port.ArchivoDeMovimientoRepository;
import tech.tetengo.api.movimientos.application.port.ResumenDeMovimientoRepository;
import tech.tetengo.api.movimientos.domain.model.ArchivoDeMovimiento;

/**
 * Deletes movement files from object storage and from the index, in batches, in the household in
 * context and the caller's transaction. Objects go first: an index row is only removed once its
 * objects were deleted, the Parquet file and its light replay file (ADR 0011; deleting a missing one is
 * fine).
 */
@Service
public class BorradoDeMovimientos {

    static final int LOTE = 500;

    private final ArchivoDeMovimientoRepository archivos;
    private final ResumenDeMovimientoRepository resumenes;
    private final AlmacenamientoDeMovimientos almacenamiento;

    public BorradoDeMovimientos(
            ArchivoDeMovimientoRepository archivos,
            ResumenDeMovimientoRepository resumenes,
            AlmacenamientoDeMovimientos almacenamiento) {
        this.archivos = archivos;
        this.resumenes = resumenes;
        this.almacenamiento = almacenamiento;
    }

    /** Every file and summary of the household; returns how many files. */
    public long todos() {
        long borrados = 0;
        for (List<ArchivoDeMovimiento> lote = archivos.algunos(LOTE); !lote.isEmpty(); lote = archivos.algunos(LOTE)) {
            borrados += borrar(lote);
        }
        resumenes.eliminarTodos();
        return borrados;
    }

    /** CA-37.4: files that ended before {@code limite}, and uploads never confirmed. */
    public long anterioresA(Instant limite, Instant pendientesAntesDe) {
        long borrados = 0;
        for (var lote = archivos.anterioresA(limite, LOTE);
                !lote.isEmpty();
                lote = archivos.anterioresA(limite, LOTE)) {
            borrados += borrar(lote);
        }
        for (var lote = archivos.pendientesAnterioresA(pendientesAntesDe, LOTE);
                !lote.isEmpty();
                lote = archivos.pendientesAnterioresA(pendientesAntesDe, LOTE)) {
            borrar(lote);
        }
        return borrados;
    }

    private long borrar(List<ArchivoDeMovimiento> lote) {
        almacenamiento.eliminar(
                lote.stream().flatMap(archivo -> archivo.claves().stream()).toList());
        archivos.eliminar(lote);
        return lote.stream().filter(ArchivoDeMovimiento::disponible).count();
    }
}
