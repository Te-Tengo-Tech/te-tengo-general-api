package tech.tetengo.api.camaras;

import java.time.Instant;
import java.util.UUID;

/**
 * CA-31.6: the PC was unlinked from itself with «Borrar también los datos guardados en esta PC»: it
 * deletes its data folder (movements, video and clips). The deletion is local; the API only records it
 * in the activity log (US-33).
 */
public record DatosDeLaPcBorrados(UUID hogarId, UUID pcId, String nombre, Instant ocurridaEn) {}
