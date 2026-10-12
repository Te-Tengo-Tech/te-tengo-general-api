package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.movimientos.domain.MovimientoError;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-37 / CA-37.2: one row of the index per movement file: 5 minutes of a camera's body landmarks in
 * Parquet with zstd, uploaded by the PC straight to object storage. PostgreSQL keeps only this index,
 * never the landmarks (change request 2.4).
 *
 * <p>ADR 0011: the same frames may also be stored as a light replay file (TTMOV v1, gzip) under the same
 * key with {@code .ttmov}, the one the app draws. The row keeps its size, SHA-256 and upload time; it is
 * available once the PC confirmed it and the storage has it, and it is deleted with the Parquet file.
 */
@Entity
@Table(name = "archivos_de_movimiento")
public class ArchivoDeMovimiento extends EntidadDelHogar {

    private static final String EXTENSION = ".parquet";
    private static final String EXTENSION_LIGERA = ".ttmov";

    private static final DateTimeFormatter RUTA =
            DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm").withZone(ZoneOffset.UTC);

    @Column(name = "camara_id", nullable = false, updatable = false)
    private UUID camaraId;

    @Column(nullable = false, updatable = false)
    private Instant desde;

    @Column(nullable = false)
    private Instant hasta;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    /** Hex SHA-256 the PC computed, so the reader can check the file. */
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private int fotogramas;

    @Column(nullable = false, updatable = false, length = 300)
    private String clave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoArchivo estado;

    @Column(name = "subido_en")
    private Instant subidoEn;

    /** ADR 0011: the light file's size, as reported by the PC and then by the storage. */
    @Column(name = "ligero_tamano_bytes")
    private Long ligeroTamanoBytes;

    @Column(name = "ligero_sha256", length = 64)
    private String ligeroSha256;

    /** Set once the light file was confirmed; null while it is not available. */
    @Column(name = "ligero_subido_en")
    private Instant ligeroSubidoEn;

    protected ArchivoDeMovimiento() {}

    public ArchivoDeMovimiento(
            UUID hogarId,
            UUID camaraId,
            Instant desde,
            Instant hasta,
            long tamanoBytes,
            String sha256,
            int fotogramas) {
        this.camaraId = Objects.requireNonNull(camaraId);
        this.desde = Objects.requireNonNull(desde);
        this.clave = clave(hogarId, camaraId, desde);
        this.estado = EstadoArchivo.PENDIENTE;
        actualizar(hasta, tamanoBytes, sha256, fotogramas);
    }

    /**
     * {@code movimientos/<hogar>/<camara>/YYYY/MM/DD/HH/mm.parquet}, in UTC, from the start of the
     * file. One file per camera and start.
     */
    public static String clave(UUID hogarId, UUID camaraId, Instant desde) {
        return "movimientos/%s/%s/%s%s".formatted(hogarId, camaraId, RUTA.format(desde), EXTENSION);
    }

    /** ADR 0011: the light file's key is the Parquet file's with {@code .ttmov} instead of {@code .parquet}. */
    public static String claveLigera(String clave) {
        String base = clave.endsWith(EXTENSION) ? clave.substring(0, clave.length() - EXTENSION.length()) : clave;
        return base + EXTENSION_LIGERA;
    }

    public String claveLigera() {
        return claveLigera(clave);
    }

    /** Every object of this file in the storage: the Parquet file and its light file (may not exist). */
    public List<String> claves() {
        return List.of(clave, claveLigera());
    }

    /** A new upload of a file still pending replaces its description. */
    public void actualizar(Instant hasta, long tamanoBytes, String sha256, int fotogramas) {
        this.hasta = Objects.requireNonNull(hasta);
        this.tamanoBytes = tamanoBytes;
        this.sha256 = Objects.requireNonNull(sha256);
        this.fotogramas = fotogramas;
    }

    /** The storage has it: it enters the index, with the size the storage reports. */
    public void confirmar(long tamanoReal, Instant ahora) {
        this.tamanoBytes = tamanoReal;
        this.estado = EstadoArchivo.DISPONIBLE;
        this.subidoEn = ahora;
    }

    public boolean disponible() {
        return estado == EstadoArchivo.DISPONIBLE;
    }

    /**
     * ADR 0011: the PC asks for the light file's upload URL. Refused once the light file is available;
     * asking again before that replaces its size and checksum.
     */
    public void prepararLigero(long tamanoBytes, String sha256) {
        if (ligeroDisponible()) {
            throw new ErrorDeNegocio(MovimientoError.ARCHIVO_YA_SUBIDO, Map.of("archivoId", getId()));
        }
        this.ligeroTamanoBytes = tamanoBytes;
        this.ligeroSha256 = Objects.requireNonNull(sha256);
    }

    /**
     * The storage has the light file: it becomes available with the size the storage reports. Returns
     * false when it already was (idempotent). Without an upload URL asked for first there is nothing to
     * confirm ({@code ARCHIVO_NO_SUBIDO}).
     */
    public boolean confirmarLigero(long tamanoReal, Instant ahora) {
        if (ligeroDisponible()) {
            return false;
        }
        if (ligeroSha256 == null) {
            throw new ErrorDeNegocio(MovimientoError.ARCHIVO_NO_SUBIDO);
        }
        this.ligeroTamanoBytes = tamanoReal;
        this.ligeroSubidoEn = Objects.requireNonNull(ahora);
        return true;
    }

    public boolean ligeroDisponible() {
        return ligeroSubidoEn != null;
    }

    /** The light file was deleted (or its upload abandoned): the row forgets it. */
    public void quitarLigero() {
        this.ligeroTamanoBytes = null;
        this.ligeroSha256 = null;
        this.ligeroSubidoEn = null;
    }

    public UUID getCamaraId() {
        return camaraId;
    }

    public Instant getDesde() {
        return desde;
    }

    public Instant getHasta() {
        return hasta;
    }

    public long getTamanoBytes() {
        return tamanoBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public int getFotogramas() {
        return fotogramas;
    }

    public String getClave() {
        return clave;
    }

    public EstadoArchivo getEstado() {
        return estado;
    }

    public Long getLigeroTamanoBytes() {
        return ligeroTamanoBytes;
    }

    public String getLigeroSha256() {
        return ligeroSha256;
    }

    public Instant getLigeroSubidoEn() {
        return ligeroSubidoEn;
    }
}
