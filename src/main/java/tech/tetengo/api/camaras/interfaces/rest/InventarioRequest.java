package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The webcams the PC detects now (US-29); an empty list when it finds none (CA-29.6). */
record InventarioRequest(@NotNull @Size(max = 32) List<@Valid Webcam> webcams) {

    record Webcam(
            @NotBlank(message = "Falta el identificador del dispositivo.") @Size(max = 300)
            String dispositivoId,

            @NotBlank(message = "Falta el nombre del dispositivo.")
            String nombreDispositivo,

            Boolean conectada) {}
}
