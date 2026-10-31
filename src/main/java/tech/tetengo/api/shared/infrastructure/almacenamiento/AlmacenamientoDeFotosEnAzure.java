package tech.tetengo.api.shared.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.ContenedorDeBlobs;

/**
 * Profile photos in an Azure Blob Storage container (ADR 0013): the app uploads through a SAS PUT and
 * reads through a SAS GET; the backend reads the upload and writes the normalized JPEG itself. A SAS
 * cannot pin the size the way S3 signs {@code Content-Length}: the confirmation checks the uploaded
 * object's size (API contract §13) and the cleanup job deletes unconfirmed uploads.
 */
class AlmacenamientoDeFotosEnAzure implements AlmacenamientoDeFotos {

    private final ContenedorDeBlobs contenedor;

    AlmacenamientoDeFotosEnAzure(ContenedorDeBlobs contenedor) {
        this.contenedor = contenedor;
    }

    @Override
    public Subida urlDeSubida(String clave, String contentType, long tamanoBytes, Instant expiraEn) {
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
    public Optional<byte[]> leer(String clave) {
        return contenedor.leer(clave);
    }

    @Override
    public void guardar(String clave, byte[] contenido, String contentType) {
        contenedor.guardar(clave, contenido, contentType);
    }

    @Override
    public void eliminar(String clave) {
        contenedor.eliminar(clave);
    }
}
