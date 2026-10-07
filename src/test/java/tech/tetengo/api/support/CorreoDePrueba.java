package tech.tetengo.api.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tech.tetengo.api.shared.application.port.NotificadorCorreo;

/** Records the e-mails instead of sending them. */
public class CorreoDePrueba implements NotificadorCorreo {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<Correo> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(Correo correo) {
        enviados.add(correo);
    }

    public List<Correo> enviados() {
        return List.copyOf(enviados);
    }

    public List<Correo> enviadosA(String para) {
        return enviados.stream().filter(c -> c.para().equals(para)).toList();
    }

    /** The one-time token of the last link sent to the address. */
    public String ultimoToken(String para) {
        List<Correo> recibidos = enviadosA(para);
        if (recibidos.isEmpty()) {
            throw new AssertionError("No se envió ningún correo a " + para);
        }
        Matcher m = TOKEN.matcher(recibidos.getLast().cuerpo());
        if (!m.find()) {
            throw new AssertionError("El correo no tiene un enlace con token");
        }
        return m.group(1);
    }

    public void limpiar() {
        enviados.clear();
    }
}
