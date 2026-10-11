package tech.tetengo.api.camaras.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

/**
 * The camera's room: by name ({@code nombreHabitacion}, CA-06.2) or by the id of a room of the
 * household ({@code ambienteId}, US-29). One of the two.
 */
record RenombrarCamaraRequest(String nombreHabitacion, UUID ambienteId) {

    @JsonIgnore
    @AssertTrue(message = "Escribe el nombre del ambiente o elige uno.")
    boolean isNombreHabitacionOAmbiente() {
        return nombreHabitacion != null || ambienteId != null;
    }
}
