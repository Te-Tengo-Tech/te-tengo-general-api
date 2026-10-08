package tech.tetengo.api.shared.infrastructure.push;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.EndpointDisabledException;
import software.amazon.awssdk.services.sns.model.InvalidParameterException;
import software.amazon.awssdk.services.sns.model.NotFoundException;
import tech.tetengo.api.shared.application.port.NotificadorPush;
import tools.jackson.databind.json.JsonMapper;

/**
 * Push through Amazon SNS mobile push. Each device gets a platform endpoint under the platform
 * application of its platform ({@code CreatePlatformEndpoint}, re-enabled if SNS had disabled it),
 * and its ARN is stored with the device. The message carries the payload of every platform
 * ({@code MessageStructure=json}): {@code GCM} in the FCM HTTP v1 format ({@code fcmV1Message}) and
 * {@code APNS} / {@code APNS_SANDBOX}, with the same content as the other providers. Web devices
 * (the PWA's FCM web push tokens) need a web platform application, an FCM one whose {@code GCM}
 * payload carries the web push block; without it they are skipped with a warning and do not count
 * as delivered. Endpoints SNS reports as disabled are reported as invalid tokens. When SNS accepts
 * no device for any other reason, it throws {@link FallaDePush} so the notice is retried (CA-16.4).
 */
class NotificadorPushSns implements NotificadorPush, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPushSns.class);

    /** SNS names the existing endpoint when the token is registered with other attributes. */
    private static final Pattern ENDPOINT_EXISTENTE = Pattern.compile("Endpoint (arn:\\S+) already exists");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SnsClient sns;
    private final Map<Plataforma, String> aplicaciones;
    private final String enlaceWeb;

    /**
     * @param arnWeb platform application of web devices; blank skips them
     * @param enlaceWeb the PWA's URL that web notifications open; null sends none
     */
    NotificadorPushSns(SnsClient sns, String arnAndroid, String arnIos, String arnWeb, String enlaceWeb) {
        if (!StringUtils.hasText(arnAndroid) || !StringUtils.hasText(arnIos)) {
            throw new IllegalStateException(
                    "tetengo.push.sns.arn-android y arn-ios (TT_SNS_ARN_ANDROID, TT_SNS_ARN_IOS) son obligatorios con SNS");
        }
        this.sns = sns;
        this.aplicaciones = new EnumMap<>(Map.of(Plataforma.ANDROID, arnAndroid, Plataforma.IOS, arnIos));
        if (StringUtils.hasText(arnWeb)) {
            aplicaciones.put(Plataforma.WEB, arnWeb);
        }
        this.enlaceWeb = enlaceWeb;
    }

    static NotificadorPushSns crear(PropiedadesDeSns propiedades, String enlaceWeb) {
        var cliente = propiedades.cliente();
        SnsClient sns = cliente.configurar(SnsClient.builder()).build();
        String android = propiedades.arnAndroid();
        String ios = propiedades.arnIos();
        String web = propiedades.arnWeb();
        if (propiedades.crearAplicaciones()) {
            // Locally (Floci) all are FCM applications: the app registers FCM tokens on iOS and the web too.
            android = StringUtils.hasText(android) ? android : crearAplicacion(sns, "te-tengo-android");
            ios = StringUtils.hasText(ios) ? ios : crearAplicacion(sns, "te-tengo-ios");
            web = StringUtils.hasText(web) ? web : crearAplicacion(sns, "te-tengo-web");
        }
        log.info(
                "Push por Amazon SNS ({}): Android {}, iOS {}, web {}",
                cliente.destino(),
                android,
                ios,
                StringUtils.hasText(web) ? web : "(sin aplicación: no se avisa a la PWA)");
        return new NotificadorPushSns(sns, android, ios, web, enlaceWeb);
    }

    private static String crearAplicacion(SnsClient sns, String nombre) {
        return sns.createPlatformApplication(
                        b -> b.name(nombre).platform("GCM").attributes(Map.of("PlatformCredential", "local")))
                .platformApplicationArn();
    }

    @Override
    public Resultado enviar(List<Destino> destinos, Aviso aviso) {
        String mensaje = mensaje(ContenidoDelAviso.de(aviso), UUID.randomUUID().toString(), enlaceWeb);
        int aceptados = 0;
        int sinAplicacion = 0;
        Set<String> invalidos = new HashSet<>();
        Map<String, String> referencias = new HashMap<>();
        SdkException ultimoError = null;
        for (Destino destino : destinos) {
            if (!aplicaciones.containsKey(destino.plataforma())) {
                sinAplicacion++;
                continue;
            }
            try {
                String endpoint = destino.referencia();
                if (endpoint == null) {
                    endpoint = endpoint(destino);
                    referencias.put(destino.tokenPush(), endpoint);
                }
                try {
                    publicar(endpoint, mensaje);
                } catch (NotFoundException e) {
                    // The endpoint was deleted (e.g. another platform application): register it again.
                    endpoint = endpoint(destino);
                    referencias.put(destino.tokenPush(), endpoint);
                    publicar(endpoint, mensaje);
                }
                aceptados++;
            } catch (EndpointDisabledException e) {
                invalidos.add(destino.tokenPush());
                referencias.remove(destino.tokenPush());
            } catch (SdkException e) {
                ultimoError = e;
                referencias.remove(destino.tokenPush());
            }
        }
        if (sinAplicacion > 0) {
            log.warn(
                    "Push {}: {} dispositivo(s) web sin aviso; SNS no tiene aplicación web (TT_SNS_ARN_WEB)",
                    aviso.tipo(),
                    sinAplicacion);
        }
        if (ultimoError != null && aceptados == 0) {
            throw new FallaDePush("SNS no aceptó el push " + aviso.tipo(), ultimoError);
        }
        if (ultimoError != null) {
            log.warn("SNS no aceptó el push {} para algunos dispositivos", aviso.tipo(), ultimoError);
        }
        return new Resultado(aceptados, invalidos, referencias);
    }

    private void publicar(String endpoint, String mensaje) {
        sns.publish(b -> b.targetArn(endpoint).messageStructure("json").message(mensaje));
    }

    /**
     * Creates or reuses the platform endpoint of the token and makes sure it is enabled and has the
     * token (AWS's recommended registration flow).
     */
    private String endpoint(Destino destino) {
        String aplicacion = aplicaciones.get(destino.plataforma());
        String arn = crearOEncontrar(aplicacion, destino.tokenPush());
        Map<String, String> atributos =
                sns.getEndpointAttributes(b -> b.endpointArn(arn)).attributes();
        if (!"true".equalsIgnoreCase(atributos.get("Enabled"))
                || !destino.tokenPush().equals(atributos.get("Token"))) {
            String endpoint = arn;
            sns.setEndpointAttributes(
                    b -> b.endpointArn(endpoint).attributes(Map.of("Token", destino.tokenPush(), "Enabled", "true")));
        }
        return arn;
    }

    private String crearOEncontrar(String aplicacion, String token) {
        try {
            return sns.createPlatformEndpoint(
                            b -> b.platformApplicationArn(aplicacion).token(token))
                    .endpointArn();
        } catch (InvalidParameterException e) {
            Matcher existente = ENDPOINT_EXISTENTE.matcher(String.valueOf(e.getMessage()));
            if (!existente.find()) {
                throw e;
            }
            return existente.group(1);
        }
    }

    /** {@code MessageStructure=json}: one payload per platform, each a JSON string. */
    static String mensaje(ContenidoDelAviso contenido, String idDelMensaje, String enlaceWeb) {
        String apns = JSON.writeValueAsString(CargasPush.apns(contenido, idDelMensaje));
        Map<String, String> porPlataforma = new LinkedHashMap<>();
        porPlataforma.put("default", contenido.titulo() + ". " + contenido.cuerpo());
        porPlataforma.put(
                "GCM",
                JSON.writeValueAsString(
                        Map.of("fcmV1Message", Map.of("message", CargasPush.mensajeFcm(contenido, enlaceWeb)))));
        porPlataforma.put("APNS", apns);
        porPlataforma.put("APNS_SANDBOX", apns);
        return JSON.writeValueAsString(porPlataforma);
    }

    @Override
    public void close() {
        sns.close();
    }
}
