package tech.tetengo.api.actividad.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tech.tetengo.api.actividad.application.port.ActividadRepository;
import tech.tetengo.api.actividad.domain.model.Actividad;

/** Stores an entry once: an entry whose key is already in the log is ignored. In the caller's household. */
@Service
public class RegistroDeActividad {

    private final ActividadRepository actividad;

    public RegistroDeActividad(ActividadRepository actividad) {
        this.actividad = actividad;
    }

    /** Returns false if it was already registered. */
    public boolean registrar(Actividad entrada) {
        if (actividad.existe(entrada.getClave())) {
            return false;
        }
        actividad.registrar(entrada);
        return true;
    }

    /** A short, stable key from the parts that identify a change. */
    public static String clave(Object... partes) {
        StringBuilder texto = new StringBuilder();
        for (Object parte : partes) {
            texto.append(parte).append('|');
        }
        return UUID.nameUUIDFromBytes(texto.toString().getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
