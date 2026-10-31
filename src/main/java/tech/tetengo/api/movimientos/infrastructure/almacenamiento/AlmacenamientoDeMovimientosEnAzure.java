package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.ContenedorDeBlobs;

/**
 * Movement files in an Azure Blob Storage container, through SAS URLs (ADR 0010, ADR 0013): the PC
 * uploads with a PUT URL and its headers, the app reads with a GET URL. Deletions of many files run
 * in parallel.
 */
class AlmacenamientoDeMovimientosEnAzure implements AlmacenamientoDeMovimientos {

    private final ContenedorDeBlobs contenedor;

    AlmacenamientoDeMovimientosEnAzure(ContenedorDeBlobs contenedor) {
        this.contenedor = contenedor;
    }

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        var firmada = contenedor.urlDeSubida(clave, contentType, expiraEn);
        return new Subida(firmada.url(), firmada.cabeceras());
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn) {
        return contenedor.urlDeLectura(clave, expiraEn, null);
    }

    @Override
    public Optional<Long> tamano(String clave) {
        return contenedor.tamano(clave);
    }

    @Override
    public void eliminar(Collection<String> claves) {
        contenedor.eliminar(claves);
    }
}
