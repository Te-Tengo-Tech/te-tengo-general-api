package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Instant;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;
import tech.tetengo.api.shared.infrastructure.almacenamiento.azure.ContenedorDeBlobs;

/**
 * Clips (and their landmarks) in an Azure Blob Storage container, through SAS URLs (ADR 0013): the
 * agent uploads with a PUT URL and the headers returned with it ({@code Content-Type}, {@code
 * x-ms-blob-type: BlockBlob}), the app plays or downloads with a GET URL; the backend never handles
 * the video bytes.
 */
class AlmacenamientoDeClipsEnAzure implements AlmacenamientoDeClips {

    private final ContenedorDeBlobs contenedor;

    AlmacenamientoDeClipsEnAzure(ContenedorDeBlobs contenedor) {
        this.contenedor = contenedor;
    }

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        var firmada = contenedor.urlDeSubida(clave, contentType, expiraEn);
        return new Subida(firmada.url(), firmada.cabeceras());
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn, boolean descarga, String nombreArchivo) {
        return contenedor.urlDeLectura(
                clave, expiraEn, descarga ? "attachment; filename=\"" + nombreArchivo + ".mp4\"" : null);
    }

    @Override
    public boolean existe(String clave) {
        return contenedor.existe(clave);
    }

    @Override
    public void eliminar(String clave) {
        contenedor.eliminar(clave);
    }
}
