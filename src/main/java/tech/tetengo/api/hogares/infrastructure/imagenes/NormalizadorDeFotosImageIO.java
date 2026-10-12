package tech.tetengo.api.hogares.infrastructure.imagenes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;
import tech.tetengo.api.hogares.application.port.NormalizadorDeFotos;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * Photos with Java ImageIO: JPEG and PNG with the JDK's readers, WebP with TwelveMonkeys'. The format
 * is told by the file's first bytes, never by its declared type. The JPEG's EXIF orientation is applied
 * before the metadata is dropped, so a phone photo taken upright stays upright.
 */
@Component
class NormalizadorDeFotosImageIO implements NormalizadorDeFotos {

    /** Decoding a huge image takes too much memory: at most 40 megapixels [implementation choice]. */
    static final long PIXELES_MAXIMOS = 40_000_000L;

    private static final float CALIDAD_JPEG = 0.85f;

    NormalizadorDeFotosImageIO() {
        // In the executable jar, ImageIO may have scanned for plugins before the WebP reader was visible.
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(false);
    }

    @Override
    public byte[] normalizar(byte[] original) {
        Formato formato = Formato.de(original).orElseThrow(NormalizadorDeFotosImageIO::noAdmitido);
        BufferedImage imagen = leer(original, formato);
        int orientacion = formato == Formato.JPEG ? OrientacionExif.de(original) : 1;
        return comoJpeg(orientar(cuadradaYReducida(imagen), orientacion));
    }

    private static BufferedImage leer(byte[] original, Formato formato) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName(formato.nombre);
            if (entrada == null || !lectores.hasNext()) {
                throw noAdmitido();
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > PIXELES_MAXIMOS) {
                    throw new ErrorDeNegocio(HogarError.FOTO_MUY_GRANDE);
                }
                BufferedImage imagen = lector.read(0);
                if (imagen == null || imagen.getWidth() < 1 || imagen.getHeight() < 1) {
                    throw noAdmitido();
                }
                return imagen;
            } finally {
                lector.dispose();
            }
        } catch (ErrorDeNegocio e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw noAdmitido();
        }
    }

    /** The centered square, scaled down to {@link #LADO_MAXIMO} at most, on white (PNG transparency). */
    static BufferedImage cuadradaYReducida(BufferedImage imagen) {
        int lado = Math.min(imagen.getWidth(), imagen.getHeight());
        int x = (imagen.getWidth() - lado) / 2;
        int y = (imagen.getHeight() - lado) / 2;
        BufferedImage actual = dibujar(imagen, x, y, lado, lado, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int destino = Math.min(lado, LADO_MAXIMO);
        // Halving step by step keeps the detail a single big reduction would alias away.
        while (actual.getWidth() / 2 >= destino) {
            actual = dibujar(
                    actual,
                    0,
                    0,
                    actual.getWidth(),
                    actual.getWidth(),
                    actual.getWidth() / 2,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (actual.getWidth() != destino) {
            actual = dibujar(
                    actual,
                    0,
                    0,
                    actual.getWidth(),
                    actual.getWidth(),
                    destino,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return actual;
    }

    private static BufferedImage dibujar(
            BufferedImage origen, int x, int y, int ancho, int alto, Object interpolacion) {
        return dibujar(origen, x, y, ancho, alto, ancho, interpolacion);
    }

    private static BufferedImage dibujar(
            BufferedImage origen, int x, int y, int ancho, int alto, int lado, Object interpolacion) {
        BufferedImage destino = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolacion);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, lado, lado);
            g.drawImage(origen, 0, 0, lado, lado, x, y, x + ancho, y + alto, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** Applies an EXIF orientation (1 to 8) to a square image. */
    static BufferedImage orientar(BufferedImage cuadrada, int orientacion) {
        if (orientacion < 2 || orientacion > 8) {
            return cuadrada;
        }
        int n = cuadrada.getWidth();
        int m = n - 1;
        BufferedImage destino = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int sx;
                int sy;
                switch (orientacion) {
                    case 2 -> {
                        sx = m - x;
                        sy = y;
                    }
                    case 3 -> {
                        sx = m - x;
                        sy = m - y;
                    }
                    case 4 -> {
                        sx = x;
                        sy = m - y;
                    }
                    case 5 -> {
                        sx = y;
                        sy = x;
                    }
                    case 6 -> {
                        sx = y;
                        sy = m - x;
                    }
                    case 7 -> {
                        sx = m - y;
                        sy = m - x;
                    }
                    default -> {
                        sx = m - y;
                        sy = x;
                    }
                }
                destino.setRGB(x, y, cuadrada.getRGB(sx, sy));
            }
        }
        return destino;
    }

    /** A plain JPEG: no EXIF, no location, nothing but the pixels. */
    private static byte[] comoJpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        var salida = new ByteArrayOutputStream();
        try (var flujo = new MemoryCacheImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(CALIDAD_JPEG);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static ErrorDeNegocio noAdmitido() {
        return new ErrorDeNegocio(HogarError.FOTO_FORMATO_NO_ADMITIDO);
    }

    /** The accepted formats, by their magic bytes. */
    enum Formato {
        JPEG("jpeg"),
        PNG("png"),
        WEBP("webp");

        private final String nombre;

        Formato(String nombre) {
            this.nombre = nombre;
        }

        static Optional<Formato> de(byte[] b) {
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return Optional.of(JPEG);
            }
            if (b.length >= 8
                    && (b[0] & 0xFF) == 0x89
                    && b[1] == 'P'
                    && b[2] == 'N'
                    && b[3] == 'G'
                    && b[4] == 0x0D
                    && b[5] == 0x0A
                    && b[6] == 0x1A
                    && b[7] == 0x0A) {
                return Optional.of(PNG);
            }
            if (b.length >= 12
                    && b[0] == 'R'
                    && b[1] == 'I'
                    && b[2] == 'F'
                    && b[3] == 'F'
                    && b[8] == 'W'
                    && b[9] == 'E'
                    && b[10] == 'B'
                    && b[11] == 'P') {
                return Optional.of(WEBP);
            }
            return Optional.empty();
        }
    }
}
