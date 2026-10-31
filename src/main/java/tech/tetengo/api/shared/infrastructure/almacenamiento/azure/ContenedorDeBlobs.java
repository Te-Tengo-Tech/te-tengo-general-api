package tech.tetengo.api.shared.infrastructure.almacenamiento.azure;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.Utility;
import com.azure.storage.common.sas.SasProtocol;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One private container of Azure Blob Storage, reached by the household agent and the app only
 * through short-lived SAS URLs (ADR 0013): a PUT URL with create and write permissions to upload one
 * blob, and a GET URL with read permission. The adapters of the clips, the movement files and the
 * photos build on it.
 */
public final class ContenedorDeBlobs {

    private static final Logger log = LoggerFactory.getLogger(ContenedorDeBlobs.class);

    /** Put Blob needs this header; it is part of the upload instructions the API returns. */
    public static final String CABECERA_TIPO_DE_BLOB = "x-ms-blob-type";

    public static final String BLOCK_BLOB = "BlockBlob";

    /** A user delegation SAS lasts at most 7 days; service SAS are capped the same way. */
    static final Duration VIGENCIA_MAXIMA = Duration.ofDays(7);

    /** Deletions of many blobs run this many at a time. */
    static final int BORRADOS_EN_PARALELO = 16;

    private static final int INTENTOS_CREAR_CONTENEDOR = 10;

    private final BlobContainerClient contenedor;
    private final FirmanteSas firmante;
    private final SasProtocol protocolo;
    private final Clock reloj;

    ContenedorDeBlobs(BlobContainerClient contenedor, FirmanteSas firmante, SasProtocol protocolo, Clock reloj) {
        this.contenedor = contenedor;
        this.firmante = firmante;
        this.protocolo = protocolo;
        this.reloj = reloj;
    }

    /** A signed upload: {@code PUT url} with exactly these {@code cabeceras}. */
    public record SubidaFirmada(URI url, Map<String, String> cabeceras) {

        public SubidaFirmada {
            cabeceras = Map.copyOf(cabeceras);
        }
    }

    public String nombre() {
        return contenedor.getBlobContainerName();
    }

    /**
     * SAS PUT URL of one blob, valid until {@code expiraEn} (1 s to 7 days from now), and the headers
     * the PUT must carry: {@code Content-Type} (stored as the blob's type) and {@code x-ms-blob-type:
     * BlockBlob}. Unlike S3's signed {@code Content-Type}, the SAS cannot pin the type or the size:
     * the use cases check the uploaded object.
     */
    public SubidaFirmada urlDeSubida(String clave, String contentType, Instant expiraEn) {
        var permisos = new BlobSasPermission().setCreatePermission(true).setWritePermission(true);
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("Content-Type", contentType);
        cabeceras.put(CABECERA_TIPO_DE_BLOB, BLOCK_BLOB);
        return new SubidaFirmada(firmar(clave, permisos, expiraEn, null), cabeceras);
    }

    /**
     * SAS GET URL of one blob. With {@code contentDisposition}, the response carries that {@code
     * Content-Disposition} (e.g. {@code attachment; filename="..."}), as S3's {@code
     * response-content-disposition}.
     */
    public URI urlDeLectura(String clave, Instant expiraEn, String contentDisposition) {
        return firmar(clave, new BlobSasPermission().setReadPermission(true), expiraEn, contentDisposition);
    }

    public Optional<Long> tamano(String clave) {
        try {
            return Optional.of(contenedor.getBlobClient(clave).getProperties().getBlobSize());
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public boolean existe(String clave) {
        return tamano(clave).isPresent();
    }

    public Optional<byte[]> leer(String clave) {
        try {
            return Optional.of(contenedor.getBlobClient(clave).downloadContent().toBytes());
        } catch (BlobStorageException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Writes (or overwrites) the blob from the API itself. */
    public void guardar(String clave, byte[] contenido, String contentType) {
        contenedor
                .getBlobClient(clave)
                .uploadWithResponse(
                        new BlobParallelUploadOptions(BinaryData.fromBytes(contenido))
                                .setHeaders(new BlobHttpHeaders().setContentType(contentType)),
                        null,
                        Context.NONE);
    }

    /** Deleting a missing blob is not an error. */
    public void eliminar(String clave) {
        contenedor.getBlobClient(clave).deleteIfExists();
    }

    /**
     * Deletes the blobs, up to {@value #BORRADOS_EN_PARALELO} at a time; missing ones are ignored. If
     * any deletion fails it throws once all were tried, so the caller keeps its index rows and retries.
     * (Blob batches were left out: against Azurite a batch answered 202 and deleted nothing, and a
     * deletion that silently does not happen is worse than a slower one.)
     */
    public void eliminar(Collection<String> claves) {
        if (claves.size() <= 1) {
            claves.forEach(this::eliminar);
            return;
        }
        Semaphore turnos = new Semaphore(BORRADOS_EN_PARALELO);
        AtomicInteger fallos = new AtomicInteger();
        AtomicReference<String> primerError = new AtomicReference<>();
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String clave : claves) {
                turnos.acquireUninterruptibly();
                hilos.submit(() -> {
                    try {
                        eliminar(clave);
                    } catch (RuntimeException e) {
                        fallos.incrementAndGet();
                        primerError.compareAndSet(null, e.getMessage());
                    } finally {
                        turnos.release();
                    }
                });
            }
        }
        if (fallos.get() > 0) {
            throw new IllegalStateException(
                    "No se pudieron eliminar %d blobs de %s: %s".formatted(fallos.get(), nombre(), primerError.get()));
        }
    }

    /**
     * Local runs and tests only: the emulator may still be starting when the API does, so try a few
     * times. If it never answers, the API starts anyway and uploads fail until it is up.
     */
    public void crearSiFalta() {
        for (int intento = 1; intento <= INTENTOS_CREAR_CONTENEDOR; intento++) {
            try {
                if (contenedor.createIfNotExists()) {
                    log.info("Contenedor {} creado", nombre());
                }
                return;
            } catch (RuntimeException e) {
                if (intento == INTENTOS_CREAR_CONTENEDOR) {
                    log.error("No se pudo crear el contenedor {}: {}", nombre(), e.getMessage());
                    return;
                }
                esperar();
            }
        }
    }

    private URI firmar(String clave, BlobSasPermission permisos, Instant expiraEn, String contentDisposition) {
        BlobClient blob = contenedor.getBlobClient(clave);
        var valores = new BlobServiceSasSignatureValues(vigenteHasta(expiraEn).atOffset(ZoneOffset.UTC), permisos)
                .setProtocol(protocolo);
        if (contentDisposition != null) {
            valores.setContentDisposition(contentDisposition);
        }
        return URI.create(urlDe(clave) + "?" + firmante.firmar(blob, valores));
    }

    /**
     * The blob's URL with its key's slashes kept (the SDK writes them as {@code %2F}, which the
     * service reads the same way), each segment URL-encoded.
     */
    private String urlDe(String clave) {
        StringJoiner ruta = new StringJoiner("/", contenedor.getBlobContainerUrl() + "/", "");
        for (String segmento : clave.split("/", -1)) {
            ruta.add(Utility.urlEncode(segmento));
        }
        return ruta.toString();
    }

    /** The use case decides when the URL expires; keep it between 1 s and 7 days from now. */
    private Instant vigenteHasta(Instant expiraEn) {
        Instant ahora = reloj.instant();
        Duration vigencia = Duration.between(ahora, expiraEn);
        if (vigencia.compareTo(Duration.ofSeconds(1)) < 0) {
            return ahora.plusSeconds(1);
        }
        return vigencia.compareTo(VIGENCIA_MAXIMA) > 0 ? ahora.plus(VIGENCIA_MAXIMA) : expiraEn;
    }

    private static void esperar() {
        try {
            Thread.sleep(Duration.ofSeconds(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
