package tech.tetengo.api.hogares.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/** API contract §13: photo uploads. */
class SubidaDeFotoTest {

    private final Instant ahora = Instant.parse("2026-10-11T15:00:00Z");
    private final UUID usuario = UUID.randomUUID();
    private final UUID hogar = UUID.randomUUID();

    @Test
    void soloJpegPngOWebpDeHasta5Mb() {
        SubidaDeFoto subida =
                SubidaDeFoto.pedir(usuario, DestinoDeFoto.YO, hogar, "image/webp", SubidaDeFoto.TAMANO_MAXIMO, ahora);
        assertThat(subida.getClave()).isEqualTo("fotos/subidas/" + subida.getId());
        assertThat(subida.getHogarId())
                .as("a user's own photo belongs to no household")
                .isNull();
        assertThat(subida.urlExpiraEn()).isEqualTo(ahora.plus(Duration.ofMinutes(10)));

        falla(
                () -> SubidaDeFoto.pedir(usuario, DestinoDeFoto.YO, null, "image/gif", 10, ahora),
                HogarError.FOTO_FORMATO_NO_ADMITIDO);
        falla(
                () -> SubidaDeFoto.pedir(
                        usuario, DestinoDeFoto.YO, null, "image/jpeg", SubidaDeFoto.TAMANO_MAXIMO + 1, ahora),
                HogarError.FOTO_MUY_GRANDE);
    }

    @Test
    void soloLaConfirmaQuienLaPidioEnElMismoHogarYDentroDeUnaHora() {
        SubidaDeFoto subida = SubidaDeFoto.pedir(usuario, DestinoDeFoto.ADULTO_MAYOR, hogar, "image/jpeg", 100, ahora);

        subida.verificarConfirmacion(usuario, hogar, ahora.plus(Duration.ofHours(1)));
        falla(() -> subida.verificarConfirmacion(UUID.randomUUID(), hogar, ahora), HogarError.SUBIDA_NO_ENCONTRADA);
        falla(() -> subida.verificarConfirmacion(usuario, UUID.randomUUID(), ahora), HogarError.SUBIDA_NO_ENCONTRADA);
        falla(
                () -> subida.verificarConfirmacion(usuario, hogar, ahora.plus(Duration.ofMinutes(61))),
                HogarError.SUBIDA_VENCIDA);
    }

    @Test
    void laFotoPropiaSeConfirmaDesdeCualquierHogar() {
        SubidaDeFoto subida = SubidaDeFoto.pedir(usuario, DestinoDeFoto.YO, null, "image/png", 100, ahora);

        subida.verificarConfirmacion(usuario, null, ahora);
        subida.verificarConfirmacion(usuario, hogar, ahora);
    }

    private static void falla(Runnable accion, HogarError error) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).error())
                .isEqualTo(error);
    }
}
