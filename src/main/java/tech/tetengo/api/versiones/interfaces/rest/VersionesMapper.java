package tech.tetengo.api.versiones.interfaces.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tech.tetengo.api.versiones.domain.model.Canal;
import tech.tetengo.api.versiones.domain.model.VersionPublicada;
import tech.tetengo.api.versiones.domain.model.Versiones;

final class VersionesMapper {

    private VersionesMapper() {}

    static VersionesResponse aRespuesta(Versiones versiones) {
        return new VersionesResponse(
                canal(versiones, Canal.WINDOWS),
                canal(versiones, Canal.MAC),
                canal(versiones, Canal.APK),
                canal(versiones, Canal.PLAY),
                canal(versiones, Canal.IOS),
                canal(versiones, Canal.PWA),
                versiones.paginaDescargas() == null
                        ? null
                        : versiones.paginaDescargas().toString());
    }

    private static VersionesResponse.VersionResponse canal(Versiones versiones, Canal canal) {
        return versiones.canal(canal).map(VersionesMapper::version).orElse(null);
    }

    private static VersionesResponse.VersionResponse version(VersionPublicada v) {
        return new VersionesResponse.VersionResponse(
                v.version().toString(),
                v.versionMinima() == null ? null : v.versionMinima().toString(),
                v.url() == null ? null : v.url().toString(),
                v.sha256(),
                v.tamanoBytes(),
                v.publicadaEn(),
                v.notas());
    }

    /** A strong ETag of the response: the same versions give the same tag. */
    static String etag(VersionesResponse respuesta) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256")
                    .digest(respuesta.toString().getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(huella, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
