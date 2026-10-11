package tech.tetengo.api.movimientos.infrastructure.almacenamiento;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tech.tetengo.api.movimientos.application.port.AlmacenamientoDeMovimientos;

/**
 * Movement files in Cloudflare R2 (S3 API) or Amazon S3, through pre-signed URLs of the AWS SDK v2: the
 * PC uploads with a PUT URL and the app reads with a GET URL (ADR 0010). Deletions go in batches of up to
 * 1000 keys (DeleteObjects).
 */
public class AlmacenamientoDeMovimientosEnS3 implements AlmacenamientoDeMovimientos, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDeMovimientosEnS3.class);
    private static final Duration VIGENCIA_MAXIMA = Duration.ofDays(7);
    private static final int LOTE_DE_BORRADO = 1000;

    private final S3Client s3;
    private final S3Presigner firmador;
    private final String bucket;
    private final Clock reloj;

    AlmacenamientoDeMovimientosEnS3(S3Client s3, S3Presigner firmador, String bucket, Clock reloj) {
        this.s3 = s3;
        this.firmador = firmador;
        this.bucket = bucket;
        this.reloj = reloj;
    }

    static AlmacenamientoDeMovimientosEnS3 crear(PropiedadesDeAlmacenamientoDeMovimientos propiedades, Clock reloj) {
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
                new AlmacenamientoDeMovimientosEnS3(cliente.build(), presigner.build(), propiedades.bucket(), reloj);
        if (propiedades.crearBucket()) {
            almacenamiento.crearBucketSiFalta();
        }
        log.info("Movimientos en S3: bucket {}", propiedades.bucket());
        return almacenamiento;
    }

    @Override
    public Subida urlDeSubida(String clave, String contentType, Instant expiraEn) {
        var firmada = firmador.presignPutObject(pedido -> pedido.signatureDuration(vigenciaHasta(expiraEn))
                .putObjectRequest(objeto -> objeto.bucket(bucket).key(clave).contentType(contentType)));
        Map<String, String> cabeceras = new LinkedHashMap<>();
        firmada.signedHeaders().forEach((nombre, valores) -> {
            if (!"host".equalsIgnoreCase(nombre)) {
                cabeceras.put(nombreCanonico(nombre), String.join(",", valores));
            }
        });
        return new Subida(URI.create(firmada.url().toString()), cabeceras);
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
    public void eliminar(Collection<String> claves) {
        List<ObjectIdentifier> objetos = new ArrayList<>();
        for (String clave : claves) {
            objetos.add(ObjectIdentifier.builder().key(clave).build());
            if (objetos.size() == LOTE_DE_BORRADO) {
                borrar(objetos);
                objetos.clear();
            }
        }
        if (!objetos.isEmpty()) {
            borrar(objetos);
        }
    }

    private void borrar(List<ObjectIdentifier> objetos) {
        var respuesta = s3.deleteObjects(
                pedido -> pedido.bucket(bucket).delete(d -> d.objects(objetos).quiet(true)));
        if (respuesta.hasErrors() && !respuesta.errors().isEmpty()) {
            throw new IllegalStateException("No se pudieron eliminar %d movimientos: %s"
                    .formatted(
                            respuesta.errors().size(),
                            respuesta.errors().getFirst().code()));
        }
    }

    @Override
    public void close() {
        firmador.close();
        s3.close();
    }

    void crearBucketSiFalta() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            try {
                s3.createBucket(b -> b.bucket(bucket));
            } catch (SdkException creacion) {
                log.error("No se pudo crear el bucket de movimientos {}: {}", bucket, creacion.getMessage());
            }
        } catch (SdkException e) {
            log.error("No se pudo revisar el bucket de movimientos {}: {}", bucket, e.getMessage());
        }
    }

    private Duration vigenciaHasta(Instant expiraEn) {
        Duration vigencia = Duration.between(reloj.instant(), expiraEn);
        if (vigencia.compareTo(Duration.ofSeconds(1)) < 0) {
            return Duration.ofSeconds(1);
        }
        return vigencia.compareTo(VIGENCIA_MAXIMA) > 0 ? VIGENCIA_MAXIMA : vigencia;
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
