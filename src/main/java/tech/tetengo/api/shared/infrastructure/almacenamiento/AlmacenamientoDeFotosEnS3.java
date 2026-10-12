package tech.tetengo.api.shared.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tech.tetengo.api.shared.application.port.AlmacenamientoDeFotos;

/**
 * Profile photos in Cloudflare R2 (S3 API) or Amazon S3, with the AWS SDK v2: the app uploads through a
 * pre-signed PUT and reads through a pre-signed GET; the backend reads the upload and writes the
 * normalized JPEG itself.
 */
public class AlmacenamientoDeFotosEnS3 implements AlmacenamientoDeFotos, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDeFotosEnS3.class);

    /** SigV4 pre-signed URLs last at most 7 days. */
    private static final Duration VIGENCIA_MAXIMA = Duration.ofDays(7);

    private static final int INTENTOS_CREAR_BUCKET = 10;

    private final S3Client s3;
    private final S3Presigner firmador;
    private final String bucket;
    private final Clock reloj;

    AlmacenamientoDeFotosEnS3(S3Client s3, S3Presigner firmador, String bucket, Clock reloj) {
        this.s3 = s3;
        this.firmador = firmador;
        this.bucket = bucket;
        this.reloj = reloj;
    }

    static AlmacenamientoDeFotosEnS3 crear(PropiedadesDeAlmacenamientoDeFotos propiedades, Clock reloj) {
        AwsCredentialsProvider credenciales = propiedades.conCredencialesFijas()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(propiedades.accessKey(), propiedades.secretKey()))
                : DefaultCredentialsProvider.builder().build();
        S3Configuration configuracion = S3Configuration.builder()
                .pathStyleAccessEnabled(propiedades.pathStyle())
                .build();
        var cliente = S3Client.builder().credentialsProvider(credenciales).serviceConfiguration(configuracion);
        var presigner = S3Presigner.builder().credentialsProvider(credenciales).serviceConfiguration(configuracion);
        if (StringUtils.hasText(propiedades.region())) {
            cliente.region(Region.of(propiedades.region()));
            presigner.region(Region.of(propiedades.region()));
        }
        if (propiedades.endpoint() != null) {
            cliente.endpointOverride(propiedades.endpoint());
            presigner.endpointOverride(propiedades.endpoint());
        }
        var almacenamiento =
                new AlmacenamientoDeFotosEnS3(cliente.build(), presigner.build(), propiedades.bucket(), reloj);
        if (propiedades.crearBucket()) {
            almacenamiento.crearBucketSiFalta();
        }
        log.info("Fotos en S3: bucket {}", propiedades.bucket());
        return almacenamiento;
    }

    @Override
    public Subida urlDeSubida(String clave, String contentType, long tamanoBytes, Instant expiraEn) {
        var firmada = firmador.presignPutObject(pedido -> pedido.signatureDuration(vigenciaHasta(expiraEn))
                .putObjectRequest(objeto -> objeto.bucket(bucket)
                        .key(clave)
                        .contentType(contentType)
                        .contentLength(tamanoBytes)));
        return new Subida(URI.create(firmada.url().toString()), cabecerasFirmadas(firmada.signedHeaders()));
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn) {
        var firmada = firmador.presignGetObject(pedido -> pedido.signatureDuration(vigenciaHasta(expiraEn))
                .getObjectRequest(objeto -> objeto.bucket(bucket).key(clave)));
        return URI.create(firmada.url().toString());
    }

    @Override
    public Optional<Long> tamano(String clave) {
        try {
            return Optional.of(
                    s3.headObject(objeto -> objeto.bucket(bucket).key(clave)).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        try {
            return Optional.of(
                    s3.getObjectAsBytes(objeto -> objeto.bucket(bucket).key(clave))
                            .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void guardar(String clave, byte[] contenido, String contentType) {
        s3.putObject(
                objeto -> objeto.bucket(bucket).key(clave).contentType(contentType), RequestBody.fromBytes(contenido));
    }

    @Override
    public void eliminar(String clave) {
        s3.deleteObject(objeto -> objeto.bucket(bucket).key(clave));
    }

    @Override
    public void close() {
        firmador.close();
        s3.close();
    }

    /** Local runs only: the store may still be starting when the API does, so try a few times. */
    void crearBucketSiFalta() {
        for (int intento = 1; intento <= INTENTOS_CREAR_BUCKET; intento++) {
            try {
                if (!existeBucket()) {
                    s3.createBucket(b -> b.bucket(bucket));
                    log.info("Bucket de fotos {} creado", bucket);
                }
                return;
            } catch (SdkException e) {
                if (intento == INTENTOS_CREAR_BUCKET) {
                    log.error("No se pudo crear el bucket de fotos {}: {}", bucket, e.getMessage());
                    return;
                }
                esperar();
            }
        }
    }

    private boolean existeBucket() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private static void esperar() {
        try {
            Thread.sleep(Duration.ofSeconds(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration vigenciaHasta(Instant expiraEn) {
        Duration vigencia = Duration.between(reloj.instant(), expiraEn);
        if (vigencia.compareTo(Duration.ofSeconds(1)) < 0) {
            return Duration.ofSeconds(1);
        }
        return vigencia.compareTo(VIGENCIA_MAXIMA) > 0 ? VIGENCIA_MAXIMA : vigencia;
    }

    /**
     * Headers the PUT must carry because they are signed ({@code Content-Type}). {@code Host} and
     * {@code Content-Length} are set by every HTTP client (browsers refuse to set them by hand).
     */
    private static Map<String, String> cabecerasFirmadas(Map<String, List<String>> firmadas) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        firmadas.forEach((nombre, valores) -> {
            if (!"host".equalsIgnoreCase(nombre) && !"content-length".equalsIgnoreCase(nombre)) {
                cabeceras.put(nombreCanonico(nombre), String.join(",", valores));
            }
        });
        return cabeceras;
    }

    private static String nombreCanonico(String nombre) {
        StringBuilder canonico = new StringBuilder(nombre.length());
        for (String parte : nombre.toLowerCase(Locale.ROOT).split("-", -1)) {
            if (!canonico.isEmpty()) {
                canonico.append('-');
            }
            if (!parte.isEmpty()) {
                canonico.append(Character.toUpperCase(parte.charAt(0))).append(parte.substring(1));
            }
        }
        return canonico.toString();
    }
}
