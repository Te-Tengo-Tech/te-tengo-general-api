package tech.tetengo.api.camaras.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

/** The room of a webcam: an existing one by id, or a name (a new name creates it). */
record AmbienteDeCamaraRequest(UUID ambienteId, String nombreAmbiente) {

    @JsonIgnore
    @AssertTrue(message = "Escribe el nombre del ambiente o elige uno.")
    boolean isAmbienteElegido() {
        return ambienteId != null || nombreAmbiente != null;
    }
}
