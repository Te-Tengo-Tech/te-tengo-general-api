package tech.tetengo.api.movimientos.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Movement data (US-35 to US-39, ADR 0010). Implementation choices except the 5-minute files, which
 * the change request fixes (the maximum length leaves room for a late file).
 *
 * @param versionTextoVigente version of the consent text the app shows; the consent records it
 * @param vigenciaUrlSubida lifetime of the PC's pre-signed PUT URLs
 * @param vigenciaUrlLectura lifetime of the app's pre-signed GET URLs (replay, clip landmarks)
 * @param duracionMaximaArchivo longest span one file may cover
 * @param tamanoMaximo largest file accepted
 * @param duracionMaximaRepeticion a replay counts as watched at most this long when it is not closed
 * @param rangoMaximoConsulta longest moment one replay request may ask for
 */
@ConfigurationProperties("tetengo.movimientos")
public record PropiedadesDeMovimientos(
        String versionTextoVigente,
        Duration vigenciaUrlSubida,
        Duration vigenciaUrlLectura,
        Duration duracionMaximaArchivo,
        DataSize tamanoMaximo,
        Duration duracionMaximaRepeticion,
        Duration rangoMaximoConsulta) {

    /** IANA media type of Apache Parquet. */
    public static final String CONTENT_TYPE = "application/vnd.apache.parquet";
}
