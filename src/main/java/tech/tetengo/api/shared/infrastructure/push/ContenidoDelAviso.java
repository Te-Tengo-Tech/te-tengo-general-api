package tech.tetengo.api.shared.infrastructure.push;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.application.port.NotificadorPush.Detalle;
import tech.tetengo.api.shared.application.port.NotificadorPush.TipoDeAlerta;
import tech.tetengo.api.shared.domain.model.ZonaHoraria;

/**
 * What every push provider sends for a notice: the title and body the phone shows when the app is
 * closed or in the background (CA-16.2), the prototype's label of the notice when it has one, and the
 * data payload of the API contract (§7) the app routes on. Every data value is a string, as FCM
 * requires; absent values are left out.
 *
 * <p>The copy is the prototype's, word for word (te-tengo-mobile-flutter
 * docs/references/screens and prototype/prototipo.html; the table with the source of each type is in
 * docs/NOTIFICATIONS.md): the older adult's first name, the room with its article and the time of the
 * household (CA-16.1). {@code DATOS_ELIMINADOS} has no prototype notice yet (docs/BLOCKERS.md). The
 * texts without a name or a time only cover values that could not be read; every notice of a
 * household normally has them.
 */
public record ContenidoDelAviso(String titulo, String cuerpo, String etiqueta, Map<String, String> datos) {

    /** Label of a fall notice on the lock screen (screens 44 and 51). */
    static final String URGENTE = "URGENTE · CAÍDA";

    /** Label of an unstable-movement notice on the lock screen (screen 49). */
    static final String SEVERIDAD_MEDIA = "SEVERIDAD MEDIA";

    /** Label of the follow-up notice «se levantó» on the lock screen (prototipo.html). */
    static final String SEGUIMIENTO = "SEGUIMIENTO";

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZonaHoraria.DEL_HOGAR);

    /** Room names of the app (lib/features/camaras/domain/camara.dart) with their article. */
    private static final Map<String, String> CON_ARTICULO = Map.of(
            "Sala", "la Sala",
            "Sala comedor", "la Sala comedor",
            "Dormitorio", "el Dormitorio",
            "Cocina", "la Cocina",
            "Pasillo", "el Pasillo",
            "Comedor", "el Comedor");

    public ContenidoDelAviso {
        datos = Collections.unmodifiableMap(new LinkedHashMap<>(datos));
    }

    public static ContenidoDelAviso de(Aviso aviso) {
        Detalle detalle = aviso.detalle();
        String hora = hora(aviso.ocurridaEn());
        String en = en(aviso.habitacion());
        String de = de(aviso.habitacion());
        String nombre = texto(detalle.adultoMayor());
        String deNombre = nombre == null ? "" : " de " + nombre;
        String[] texto =
                switch (aviso.tipo()) {
                    case ALERTA_CAIDA ->
                        new String[] {
                            "Posible caída" + deNombre + en,
                            conHora(hora, "Toca para ver qué hacer y llamarla."),
                            URGENTE
                        };
                    case ALERTA_MOVIMIENTO_INESTABLE ->
                        new String[] {
                            nombre == null
                                    ? "Movimiento inestable" + en
                                    : nombre + " tuvo un movimiento inestable" + en,
                            conHora(hora, "No es una caída. Revisa cómo está."),
                            SEVERIDAD_MEDIA
                        };
                    case ALERTA_ACTUALIZADA_A_CAIDA ->
                        new String[] {
                            "Ahora: posible caída" + deNombre + en,
                            conHora(hora, "Empezó como movimiento inestable" + aLas(hora(detalle.desde())) + "."),
                            URGENTE
                        };
                    case CAIDA_CONFIRMADA ->
                        new String[] {
                            nombre == null ? "Sigue en el suelo" : nombre + " sigue en el suelo",
                            "Caída confirmada" + aLas(hora) + ". La alerta sigue activa.",
                            null
                        };
                    case SE_LEVANTO ->
                        new String[] {
                            nombre == null ? "Se levantó" : nombre + " se levantó",
                            conHora(hora, "Se puso de pie" + en + ". Confirma cómo está."),
                            SEGUIMIENTO
                        };
                    case ALERTA_ATENDIDA -> atendida(detalle, hora, en);
                    case ALERTA_ESCALADA ->
                        new String[] {
                            "Nadie atendió la alerta: te toca",
                            detalle.esperaMinutos() == null
                                    ? "Nadie respondió a tiempo. Eres el contacto secundario."
                                    : "Pasaron " + detalle.esperaMinutos()
                                            + " min sin respuesta. Eres el contacto secundario.",
                            etiqueta(detalle.tipoDeAlerta())
                        };
                    case SIN_CONTACTO_SECUNDARIO ->
                        new String[] {
                            "No hay a quién escalar",
                            detalle.esperaMinutos() == null
                                    ? "No hay contacto secundario."
                                    : "Pasaron " + detalle.esperaMinutos() + " min y no hay contacto secundario.",
                            etiqueta(detalle.tipoDeAlerta())
                        };
                    case CAMARA_DESCONECTADA ->
                        new String[] {
                            "La cámara" + de + " se desconectó",
                            "Revisa el cable de la cámara, que la PC esté encendida y el internet de la casa.",
                            null
                        };
                    case CAMARA_RECONECTADA ->
                        new String[] {
                            "La cámara" + de + " volvió a estar en línea",
                            "El monitoreo se restableció" + aLas(hora) + ".",
                            null
                        };
                    case DETECCION_NO_CONFIABLE ->
                        new String[] {
                            "La detección no es confiable" + en,
                            "Hace más de 5 minutos que la cámara no ve bien"
                                    + (nombre == null ? "" : " a " + nombre)
                                    + ". Revisa la luz y el encuadre.",
                            null
                        };
                    case PAUSA_FINALIZADA ->
                        new String[] {"La cámara" + de + " se reactivó", "Terminó la pausa" + aLas(hora) + ".", null};
                    case DATOS_ELIMINADOS ->
                        new String[] {
                            "Se eliminaron las grabaciones",
                            "Se borraron las grabaciones al revocar el consentimiento.",
                            null
                        };
                };
        return new ContenidoDelAviso(texto[0], texto[1], texto[2], datos(aviso, texto[2]));
    }

    /** «Carmen atendió la alerta» / «10:46 · Caída en la Sala.» (prototipo.html, another member attends). */
    private static String[] atendida(Detalle detalle, String hora, String en) {
        String quien = texto(detalle.quien());
        String titulo = quien == null ? "Alerta atendida" : quien + " atendió la alerta";
        if (detalle.tipoDeAlerta() == null) {
            return new String[] {titulo, conHora(hora, "Toca para ver quién la atendió y cuándo."), null};
        }
        String tipo = detalle.tipoDeAlerta() == TipoDeAlerta.CAIDA ? "Caída" : "Movimiento inestable";
        return new String[] {titulo, conHora(hora, tipo + en + "."), null};
    }

    /** The lock-screen label of an escalated alert follows its type, as in the prototype. */
    private static String etiqueta(TipoDeAlerta tipo) {
        if (tipo == null) {
            return null;
        }
        return tipo == TipoDeAlerta.CAIDA ? URGENTE : SEVERIDAD_MEDIA;
    }

    /**
     * API contract §7: {@code {tipo, alertaId?, camaraId?, habitacion?, ocurridaEn}}, plus {@code etiqueta}
     * when the notice has a label: Android shows no subtitle, so the app can read it from the data.
     */
    private static Map<String, String> datos(Aviso aviso, String etiqueta) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("tipo", aviso.tipo().name());
        if (aviso.alertaId() != null) {
            datos.put("alertaId", aviso.alertaId().toString());
        }
        if (aviso.camaraId() != null) {
            datos.put("camaraId", aviso.camaraId().toString());
        }
        if (aviso.habitacion() != null && !aviso.habitacion().isBlank()) {
            datos.put("habitacion", aviso.habitacion());
        }
        if (aviso.ocurridaEn() != null) {
            datos.put("ocurridaEn", aviso.ocurridaEn().toString());
        }
        if (etiqueta != null) {
            datos.put("etiqueta", etiqueta);
        }
        return datos;
    }

    private static String hora(Instant instante) {
        return instante == null ? null : HORA.format(instante);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String conHora(String hora, String texto) {
        return hora == null ? texto : hora + " · " + texto;
    }

    /** « a las 10:42», or nothing without a time. */
    private static String aLas(String hora) {
        return hora == null ? "" : " a las " + hora;
    }

    /** « en la Sala», or nothing without a room. */
    private static String en(String habitacion) {
        return habitacion == null || habitacion.isBlank()
                ? ""
                : " en " + CON_ARTICULO.getOrDefault(habitacion, habitacion);
    }

    /** « de la Sala», « del Dormitorio», or nothing without a room. */
    private static String de(String habitacion) {
        if (habitacion == null || habitacion.isBlank()) {
            return "";
        }
        String articulo = CON_ARTICULO.get(habitacion);
        if (articulo == null) {
            return " de " + habitacion;
        }
        return articulo.startsWith("el ") ? " del " + articulo.substring(3) : " de " + articulo;
    }
}
