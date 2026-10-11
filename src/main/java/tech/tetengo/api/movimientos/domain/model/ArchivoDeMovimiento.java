package tech.tetengo.api.movimientos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import tech.tetengo.api.shared.domain.model.EntidadDelHogar;

/**
 * US-37 / CA-37.2: one row of the index per movement file: 5 minutes of a camera's body landmarks in
 * Parquet with zstd, uploaded by the PC straight to object storage. PostgreSQL keeps only this index,
 * never the landmarks (change request 2.4).
 */
@Entity
@Table(name = "archivos_de_movimiento")
public class ArchivoDeMovimiento extends EntidadDelHogar {

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
        return "movimientos/%s/%s/%s.parquet".formatted(hogarId, camaraId, RUTA.format(desde));
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
}
