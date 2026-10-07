package tech.tetengo.api.shared.infrastructure.push;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import tech.tetengo.api.shared.application.port.NotificadorPush.Aviso;
import tech.tetengo.api.shared.domain.model.ZonaHoraria;

/**
 * What every push provider sends for a notice: the title and body the phone shows when the app is
 * closed or in the background (CA-16.2), and the data payload of the API contract (§7) the app routes
 * on. Every data value is a string, as FCM requires; absent values are left out.
 *
 * <p>The copy follows the prototype and the app's in-app notices [implementation choice: the backlog
 * and the API contract define no push text]. It has the room and the time of the household (CA-16.1)
 * but not the older adult's name, which the notice does not carry (docs/BLOCKERS.md).
 */
public record ContenidoDelAviso(String titulo, String cuerpo, Map<String, String> datos) {

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
        String hora = aviso.ocurridaEn() == null ? null : HORA.format(aviso.ocurridaEn());
        String en = en(aviso.habitacion());
        String de = de(aviso.habitacion());
        String[] texto =
                switch (aviso.tipo()) {
                    case ALERTA_CAIDA -> new String[] {"Posible caída" + en, conHora(hora, "Toca para ver qué hacer.")};
                    case ALERTA_MOVIMIENTO_INESTABLE ->
                        new String[] {"Movimiento inestable" + en, conHora(hora, "No es una caída. Revisa cómo está.")};
                    case ALERTA_ACTUALIZADA_A_CAIDA ->
                        new String[] {"Ahora: posible caída" + en, conHora(hora, "Empezó como movimiento inestable.")};
                    case CAIDA_CONFIRMADA ->
                        new String[] {
                            "Caída confirmada" + en, conHora(hora, "Sigue en el suelo. La alerta sigue activa.")
                        };
                    case SE_LEVANTO ->
                        new String[] {
                            "Se levantó después de la caída",
                            conHora(hora, "Se puso de pie" + en + ". Confirma cómo está.")
                        };
                    case ALERTA_ATENDIDA ->
                        new String[] {"Otro familiar respondió a la alerta", "Toca para ver quién la atendió y cuándo."
                        };
                    case ALERTA_ESCALADA ->
                        new String[] {
                            "Nadie atendió la alerta: te toca",
                            "Nadie respondió a tiempo la alerta" + de + ". Eres el contacto secundario."
                        };
                    case SIN_CONTACTO_SECUNDARIO ->
                        new String[] {
                            "No hay contacto secundario",
                            "Nadie atendió la alerta" + de + ". La alerta sigue siendo tuya."
                        };
                    case CAMARA_DESCONECTADA ->
                        new String[] {
                            "La cámara" + de + " se desconectó",
                            "Revisa el cable de la cámara, que la PC esté encendida y el internet de la casa."
                        };
                    case CAMARA_RECONECTADA ->
                        new String[] {
                            "La cámara" + de + " volvió a estar en línea",
                            hora == null
                                    ? "El monitoreo se restableció."
                                    : "El monitoreo se restableció a las " + hora + "."
                        };
                    case DETECCION_NO_CONFIABLE ->
                        new String[] {
                            "La detección no es confiable" + en,
                            "Hace más de 5 minutos que la cámara no ve bien. Revisa la luz y el encuadre."
                        };
                    case PAUSA_FINALIZADA ->
                        new String[] {
                            "La cámara" + de + " se reactivó",
                            hora == null ? "Terminó la pausa." : "Terminó la pausa a las " + hora + "."
                        };
                    case DATOS_ELIMINADOS ->
                        new String[] {
                            "Se eliminaron las grabaciones", "Se borraron las grabaciones al revocar el consentimiento."
                        };
                };
        return new ContenidoDelAviso(texto[0], texto[1], datos(aviso));
    }

    /** API contract §7: {@code {tipo, alertaId?, camaraId?, habitacion?, ocurridaEn}}. */
    private static Map<String, String> datos(Aviso aviso) {
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
        return datos;
    }

    private static String conHora(String hora, String texto) {
        return hora == null ? texto : hora + " · " + texto;
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
