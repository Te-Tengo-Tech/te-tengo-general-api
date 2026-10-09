package tech.tetengo.api.alertas.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tech.tetengo.api.alertas.application.port.AlmacenamientoDeClips;

/**
 * Clips in Amazon S3 or an S3-compatible store (Floci locally), through pre-signed URLs of the
 * AWS SDK v2: the agent uploads with a PUT URL and the app plays or downloads with a GET URL; the
 * backend never handles the video bytes.
 */
public class AlmacenamientoDeClipsEnS3 implements AlmacenamientoDeClips, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDeClipsEnS3.class);

    /** SigV4 pre-signed URLs last at most 7 days. */
    private static final Duration VIGENCIA_MAXIMA = Duration.ofDays(7);

    private static final int INTENTOS_CREAR_BUCKET = 10;

    private final S3Client s3;
    private final S3Presigner firmador;
    private final String bucket;
    private final Clock reloj;

    AlmacenamientoDeClipsEnS3(S3Client s3, S3Presigner firmador, String bucket, Clock reloj) {
        this.s3 = s3;
        this.firmador = firmador;
        this.bucket = bucket;
        this.reloj = reloj;
    }

    static AlmacenamientoDeClipsEnS3 crear(PropiedadesDeS3 propiedades, Clock reloj) {
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
                new AlmacenamientoDeClipsEnS3(cliente.build(), presigner.build(), propiedades.bucket(), reloj);
        if (propiedades.crearBucket()) {
            almacenamiento.crearBucketSiFalta();
        }
        log.info(
                "Clips en S3: bucket {}{}",
                propiedades.bucket(),
                propiedades.endpoint() == null ? "" : " en " + propiedades.endpoint());
        return almacenamiento;
    }

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        var firmada = firmador.presignPutObject(pedido -> pedido.signatureDuration(vigenciaHasta(expiraEn))
                .putObjectRequest(objeto -> objeto.bucket(bucket).key(clave).contentType(contentType)));
        return new Subida(URI.create(firmada.url().toString()), cabecerasFirmadas(firmada.signedHeaders()));
    }

    @Override
    public URI urlDeLectura(String clave, Instant expiraEn, boolean descarga, String nombreArchivo) {
        var firmada = firmador.presignGetObject(pedido -> pedido.signatureDuration(vigenciaHasta(expiraEn))
                .getObjectRequest(objeto -> {
                    objeto.bucket(bucket).key(clave);
                    if (descarga) {
                        objeto.responseContentDisposition("attachment; filename=\"" + nombreArchivo + ".mp4\"");
                    }
                }));
        return URI.create(firmada.url().toString());
    }

    @Override
    public boolean existe(String clave) {
        try {
            s3.headObject(objeto -> objeto.bucket(bucket).key(clave));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
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

    /**
     * Local runs only: the store may still be starting when the API does, so try a few times. If it
     * never answers, the API starts anyway and uploads fail until the store is up.
     */
    void crearBucketSiFalta() {
        for (int intento = 1; intento <= INTENTOS_CREAR_BUCKET; intento++) {
            try {
                if (!existeBucket()) {
                    s3.createBucket(b -> b.bucket(bucket));
                    log.info("Bucket de clips {} creado", bucket);
                }
                return;
            } catch (SdkException e) {
                if (intento == INTENTOS_CREAR_BUCKET) {
                    log.error("No se pudo crear el bucket de clips {}: {}", bucket, e.getMessage());
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

    /** The use case decides when the URL expires; S3 wants a duration of 1 s to 7 days. */
    private Duration vigenciaHasta(Instant expiraEn) {
        Duration vigencia = Duration.between(reloj.instant(), expiraEn);
        if (vigencia.compareTo(Duration.ofSeconds(1)) < 0) {
            return Duration.ofSeconds(1);
        }
        return vigencia.compareTo(VIGENCIA_MAXIMA) > 0 ? VIGENCIA_MAXIMA : vigencia;
    }

    /**
     * Headers the PUT must carry because they are signed ({@code Content-Type}); {@code Host} is set
     * by every HTTP client.
     */
    private static Map<String, String> cabecerasFirmadas(Map<String, List<String>> firmadas) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        firmadas.forEach((nombre, valores) -> {
            if (!"host".equalsIgnoreCase(nombre)) {
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
