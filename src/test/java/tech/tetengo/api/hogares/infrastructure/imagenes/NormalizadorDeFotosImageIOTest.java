package tech.tetengo.api.hogares.infrastructure.imagenes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** API contract §13: the stored photo is a square JPEG of 512 × 512 at most, without metadata. */
class NormalizadorDeFotosImageIOTest {

    /** 60 × 20, lossless: red, blue in the middle third, red. */
    private static final byte[] WEBP =
            Base64.getDecoder().decode("UklGRioAAABXRUJQVlA4TB0AAAAvO8AEAA8Q87//8x8OCLONZnD+tDW4d0T/gxX7BQA=");

    private final NormalizadorDeFotosImageIO normalizador = new NormalizadorDeFotosImageIO();

    @Test
    void unJpegGrandeQuedaCuadradoDe512ConElCentro() throws IOException {
        byte[] foto = normalizador.normalizar(imagen("jpeg", 2000, 1000, BufferedImage.TYPE_INT_RGB));

        BufferedImage leida = leer(foto);
        assertThat(leida.getWidth()).isEqualTo(512);
        assertThat(leida.getHeight()).isEqualTo(512);
        assertThat(esAzul(leida.getRGB(256, 256))).isTrue();
        assertThat(esAzul(leida.getRGB(5, 256))).isTrue();
        assertThat(foto[0] & 0xFF).isEqualTo(0xFF);
        assertThat(foto[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void unPngConTransparenciaQuedaComoJpegSobreBlancoSinAgrandarse() throws IOException {
        byte[] foto = normalizador.normalizar(imagen("png", 300, 400, BufferedImage.TYPE_INT_ARGB));

        BufferedImage leida = leer(foto);
        assertThat(leida.getWidth()).isEqualTo(300);
        assertThat(leida.getHeight()).isEqualTo(300);
    }

    @Test
    void unWebpSeLee() throws IOException {
        BufferedImage leida = leer(normalizador.normalizar(WEBP));

        assertThat(leida.getWidth()).isEqualTo(20);
        assertThat(esAzul(leida.getRGB(10, 10))).isTrue();
    }

    @Test
    void quitaLosMetadatosYAplicaLaOrientacion() throws IOException {
        // 200 × 100: left half red, right half blue; the centered square is split in the middle.
        BufferedImage original = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(100, 0, 100, 100);
        g.dispose();
        byte[] conExif = conOrientacion(codificar(original, "jpeg"), 6);

        byte[] foto = normalizador.normalizar(conExif);

        assertThat(new String(foto, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("Exif");
        BufferedImage leida = leer(foto);
        // Rotated 90° clockwise: red goes to the top, blue to the bottom.
        assertThat(esRojo(leida.getRGB(50, 10))).isTrue();
        assertThat(esAzul(leida.getRGB(50, 90))).isTrue();
    }

    @Test
    void algoQueNoEsUnaImagenSeRechaza() {
        rechaza("hola, no soy una foto".getBytes(), HogarError.FOTO_FORMATO_NO_ADMITIDO);
        // A JPEG's magic bytes with garbage after them.
        rechaza(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5}, HogarError.FOTO_FORMATO_NO_ADMITIDO);
    }

    @Test
    void unaImagenConDemasiadosPixelesSeRechazaSinDecodificarla() throws IOException {
        // A PNG header claiming 10000 × 10000 pixels.
        byte[] png = imagen("png", 10, 10, BufferedImage.TYPE_INT_RGB);
        escribirEnteroGrande(png, 16, 10_000);
        escribirEnteroGrande(png, 20, 10_000);

        rechaza(png, HogarError.FOTO_MUY_GRANDE);
    }

    @Test
    void lasOrientacionesGiranUnCuadrado() {
        BufferedImage cuadrada = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        cuadrada.setRGB(0, 0, 1);
        cuadrada.setRGB(1, 0, 2);
        cuadrada.setRGB(0, 1, 3);
        cuadrada.setRGB(1, 1, 4);

        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 1))).containsExactly(1, 2, 3, 4);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 2))).containsExactly(2, 1, 4, 3);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 3))).containsExactly(4, 3, 2, 1);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 4))).containsExactly(3, 4, 1, 2);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 5))).containsExactly(1, 3, 2, 4);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 6))).containsExactly(3, 1, 4, 2);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 7))).containsExactly(4, 2, 3, 1);
        assertThat(fila(NormalizadorDeFotosImageIO.orientar(cuadrada, 8))).containsExactly(2, 4, 1, 3);
    }

    @Test
    void sinExifLaOrientacionEsLaOriginal() throws IOException {
        assertThat(OrientacionExif.de(imagen("jpeg", 10, 10, BufferedImage.TYPE_INT_RGB)))
                .isEqualTo(1);
        assertThat(OrientacionExif.de(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0}))
                .isEqualTo(1);
    }

    private void rechaza(byte[] bytes, HogarError error) {
        assertThatThrownBy(() -> normalizador.normalizar(bytes))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(error);
    }

    private static int[] fila(BufferedImage imagen) {
        return new int[] {
            imagen.getRGB(0, 0) & 0xFFFFFF,
            imagen.getRGB(1, 0) & 0xFFFFFF,
            imagen.getRGB(0, 1) & 0xFFFFFF,
            imagen.getRGB(1, 1) & 0xFFFFFF
        };
    }

    /** Red, with a blue square in the middle whose side is the shorter side. */
    private static byte[] imagen(String formato, int ancho, int alto, int tipo) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.BLUE);
        int lado = Math.min(ancho, alto);
        g.fillRect((ancho - lado) / 2, (alto - lado) / 2, lado, lado);
        g.dispose();
        return codificar(imagen, formato);
    }

    private static byte[] codificar(BufferedImage imagen, String formato) throws IOException {
        var salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, formato, salida);
        return salida.toByteArray();
    }

    /** Inserts an EXIF APP1 segment (big-endian) with only the orientation, right after SOI. */
    static byte[] conOrientacion(byte[] jpeg, int orientacion) {
        byte[] tiff = {
            'M',
            'M',
            0,
            42,
            0,
            0,
            0,
            8, // header, IFD0 at 8
            0,
            1, // one entry
            0x01,
            0x12,
            0,
            3,
            0,
            0,
            0,
            1,
            0,
            (byte) orientacion,
            0,
            0, // Orientation, SHORT, 1
            0,
            0,
            0,
            0 // no next IFD
        };
        int largo = 2 + 6 + tiff.length;
        var salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, 2);
        salida.write(0xFF);
        salida.write(0xE1);
        salida.write(largo >> 8);
        salida.write(largo & 0xFF);
        salida.writeBytes(new byte[] {'E', 'x', 'i', 'f', 0, 0});
        salida.writeBytes(tiff);
        salida.write(jpeg, 2, jpeg.length - 2);
        return salida.toByteArray();
    }

    private static void escribirEnteroGrande(byte[] b, int i, int valor) {
        b[i] = (byte) (valor >>> 24);
        b[i + 1] = (byte) (valor >>> 16);
        b[i + 2] = (byte) (valor >>> 8);
        b[i + 3] = (byte) valor;
    }

    private static BufferedImage leer(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static boolean esAzul(int rgb) {
        Color c = new Color(rgb);
        return c.getBlue() > 200 && c.getRed() < 60 && c.getGreen() < 60;
    }

    private static boolean esRojo(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getBlue() < 60 && c.getGreen() < 60;
    }
}
