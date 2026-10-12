package tech.tetengo.api.hogares.application.port;

/**
 * Turns an uploaded image into the stored photo (API contract §13): it checks that it really is a JPEG,
 * PNG or WebP, crops it to a centered square, scales it down to 512 × 512 at most and writes it as a
 * JPEG without metadata (EXIF, location).
 */
public interface NormalizadorDeFotos {

    /** The size of the stored photo's side, at most. */
    int LADO_MAXIMO = 512;

    /**
     * @throws tech.tetengo.api.shared.domain.exception.ErrorDeNegocio {@code 415 FOTO_FORMATO_NO_ADMITIDO}
     *     when it is not a decodable image, {@code 413 FOTO_MUY_GRANDE} when it has too many pixels
     */
    byte[] normalizar(byte[] original);
}
