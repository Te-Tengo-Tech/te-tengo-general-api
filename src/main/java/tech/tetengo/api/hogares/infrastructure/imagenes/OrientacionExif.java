package tech.tetengo.api.hogares.infrastructure.imagenes;

/**
 * Reads the EXIF orientation (tag 0x0112 of IFD0) of a JPEG, 1 to 8. Anything missing or malformed is 1
 * (as stored): the photo is only ever shown less rotated, never refused.
 */
final class OrientacionExif {

    private static final int SIN_ROTAR = 1;

    private OrientacionExif() {}

    static int de(byte[] jpeg) {
        try {
            int i = 2;
            while (i + 4 <= jpeg.length && u8(jpeg, i) == 0xFF) {
                int marcador = u8(jpeg, i + 1);
                if (marcador == 0xDA || marcador == 0xD9) {
                    return SIN_ROTAR;
                }
                int largo = u16(jpeg, i + 2, false);
                if (marcador == 0xE1 && esExif(jpeg, i + 4)) {
                    return enTiff(jpeg, i + 10, i + 2 + largo);
                }
                i += 2 + largo;
            }
        } catch (RuntimeException e) {
            return SIN_ROTAR;
        }
        return SIN_ROTAR;
    }

    private static boolean esExif(byte[] b, int i) {
        return i + 6 <= b.length
                && b[i] == 'E'
                && b[i + 1] == 'x'
                && b[i + 2] == 'i'
                && b[i + 3] == 'f'
                && b[i + 4] == 0
                && b[i + 5] == 0;
    }

    private static int enTiff(byte[] b, int tiff, int fin) {
        boolean intel = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!intel && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
            return SIN_ROTAR;
        }
        int ifd = tiff + (int) u32(b, tiff + 4, intel);
        int entradas = u16(b, ifd, intel);
        for (int e = 0; e < entradas; e++) {
            int entrada = ifd + 2 + e * 12;
            if (entrada + 12 > fin) {
                return SIN_ROTAR;
            }
            if (u16(b, entrada, intel) == 0x0112) {
                int valor = u16(b, entrada + 8, intel);
                return valor >= 1 && valor <= 8 ? valor : SIN_ROTAR;
            }
        }
        return SIN_ROTAR;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i, boolean intel) {
        return intel ? u8(b, i) | (u8(b, i + 1) << 8) : (u8(b, i) << 8) | u8(b, i + 1);
    }

    private static long u32(byte[] b, int i, boolean intel) {
        return intel
                ? (u16(b, i, true) & 0xFFFFL) | ((u16(b, i + 2, true) & 0xFFFFL) << 16)
                : ((u16(b, i, false) & 0xFFFFL) << 16) | (u16(b, i + 2, false) & 0xFFFFL);
    }
}
