package tech.tetengo.api.camaras.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * «Usar esta cámara» on the PC (CA-29.2): the webcam by the operating system's unique id (CA-29.4),
 * its name, and the room by id or by name (a new name creates it).
 */
record CamaraDeLaPcRequest(
        @NotBlank(message = "Falta el identificador del dispositivo.") @Size(max = 300)
        String dispositivoId,

        @NotBlank(message = "Falta el nombre del dispositivo.")
        String nombreDispositivo,

        UUID ambienteId,
        String nombreAmbiente) {

    @JsonIgnore
    @AssertTrue(message = "Escribe el nombre del ambiente o elige uno.")
    boolean isAmbienteElegido() {
        return ambienteId != null || nombreAmbiente != null;
    }
}
