package tech.tetengo.api.hogares.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.AggregateRoot;

/**
 * API contract §13: a photo the app uploads through a pre-signed PUT and then confirms. Global, not
 * per household: a user without a household can upload their own photo. The older adult's photo
 * keeps the household it was asked in, so it cannot be confirmed from another one.
 */
@Entity
@Table(name = "subidas_de_fotos")
public class SubidaDeFoto extends AggregateRoot {

    /** At most 5 MB [implementation choice]. */
    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    public static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp");

    /** The pre-signed PUT is valid 10 minutes. */
    public static final Duration VIGENCIA_DE_LA_URL = Duration.ofMinutes(10);

    /** It can be confirmed up to 1 hour after it was asked for. */
    public static final Duration PLAZO_DE_CONFIRMACION = Duration.ofHours(1);

    /** Unconfirmed uploads are deleted after 24 hours. */
    public static final Duration CONSERVACION = Duration.ofHours(24);

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DestinoDeFoto destino;

    @Column(name = "hogar_id", updatable = false)
    private UUID hogarId;

    @Column(nullable = false, updatable = false, length = 20)
    private String tipo;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    @Column(nullable = false, updatable = false, length = 200)
    private String clave;

    @Column(name = "pedida_en", nullable = false, updatable = false)
    private Instant pedidaEn;

    protected SubidaDeFoto() {}

    private SubidaDeFoto(
            UUID usuarioId, DestinoDeFoto destino, UUID hogarId, String tipo, long tamanoBytes, Instant pedidaEn) {
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.destino = Objects.requireNonNull(destino, "destino");
        this.hogarId = hogarId;
        this.tipo = tipo;
        this.tamanoBytes = tamanoBytes;
        this.pedidaEn = Objects.requireNonNull(pedidaEn, "pedidaEn");
        this.clave = "fotos/subidas/" + getId();
    }

    /**
     * Asks for an upload of a JPEG, PNG or WebP of at most 5 MB ({@code 415 FOTO_FORMATO_NO_ADMITIDO},
     * {@code 413 FOTO_MUY_GRANDE}). {@code hogarId} is required for the older adult's photo.
     */
    public static SubidaDeFoto pedir(
            UUID usuarioId, DestinoDeFoto destino, UUID hogarId, String tipo, long tamanoBytes, Instant ahora) {
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new ErrorDeNegocio(HogarError.FOTO_FORMATO_NO_ADMITIDO);
        }
        if (tamanoBytes > TAMANO_MAXIMO) {
            throw new ErrorDeNegocio(HogarError.FOTO_MUY_GRANDE);
        }
        if (destino == DestinoDeFoto.ADULTO_MAYOR) {
            Objects.requireNonNull(hogarId, "hogarId");
        }
        return new SubidaDeFoto(
                usuarioId, destino, destino == DestinoDeFoto.YO ? null : hogarId, tipo, tamanoBytes, ahora);
    }

    /**
     * Checks that the caller may confirm it now: only who asked for it, and for the older adult's
     * photo only from the same household ({@code 404 SUBIDA_NO_ENCONTRADA} otherwise, as if it did not
     * exist); within an hour ({@code 410 SUBIDA_VENCIDA}).
     */
    public void verificarConfirmacion(UUID usuarioId, UUID hogarActual, Instant ahora) {
        boolean ajena = !this.usuarioId.equals(usuarioId)
                || (destino == DestinoDeFoto.ADULTO_MAYOR && !hogarId.equals(hogarActual));
        if (ajena) {
            throw new ErrorDeNegocio(HogarError.SUBIDA_NO_ENCONTRADA);
        }
        if (ahora.isAfter(pedidaEn.plus(PLAZO_DE_CONFIRMACION))) {
            throw new ErrorDeNegocio(HogarError.SUBIDA_VENCIDA);
        }
    }

    public Instant urlExpiraEn() {
        return pedidaEn.plus(VIGENCIA_DE_LA_URL);
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public DestinoDeFoto getDestino() {
        return destino;
    }

    public UUID getHogarId() {
        return hogarId;
    }

    public String getTipo() {
        return tipo;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public String getClave() {
        return clave;
    }

    public Instant getPedidaEn() {
        return pedidaEn;
    }
}
