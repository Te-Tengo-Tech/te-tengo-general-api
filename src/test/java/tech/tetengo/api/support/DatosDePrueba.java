package tech.tetengo.api.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tech.tetengo.api.shared.domain.model.Rol;

/** Direct inserts for test setup when the flow itself is not what the test covers. */
public final class DatosDePrueba {

    private DatosDePrueba() {}

    /** A household owned by {@code titularId}, with its owner membership. */
    public static UUID hogar(JdbcTemplate jdbc, UUID titularId, String nombreAdultoMayor) {
        UUID hogar = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into hogares (id, titular_id, adulto_mayor_nombre, adulto_mayor_direccion,"
                        + " adulto_mayor_convivencia, creado_en, actualizado_en) values (?, ?, ?, ?, 'SOLO', ?, ?)",
                hogar,
                titularId,
                nombreAdultoMayor,
                "Av. Siempre Viva 742",
                ahora,
                ahora);
        membresia(jdbc, hogar, titularId, Rol.TITULAR);
        return hogar;
    }

    /** A household with a random owner. */
    public static UUID hogar(JdbcTemplate jdbc) {
        return hogar(jdbc, UUID.randomUUID(), "Rosa");
    }

    /** An installation credential of the household (what scripts/create-installation.sh creates). */
    public static String instalacion(JdbcTemplate jdbc, UUID hogar) {
        String credencial = "credencial-" + UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into instalaciones (id, hogar_id, credencial_hash, creado_en, actualizado_en)"
                        + " values (?, ?, encode(sha256(convert_to(?, 'UTF8')), 'hex'), ?, ?)",
                UUID.randomUUID(),
                hogar,
                credencial,
                ahora,
                ahora);
        return credencial;
    }

    /** A push device of the user (API contract §7). */
    public static void dispositivo(JdbcTemplate jdbc, UUID usuario, String tokenPush) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into dispositivos (id, token_push, usuario_id, plataforma, creado_en, actualizado_en)"
                        + " values (?, ?, ?, 'ANDROID', ?, ?)",
                UUID.randomUUID(),
                tokenPush,
                usuario,
                ahora,
                ahora);
    }

    public static void membresia(JdbcTemplate jdbc, UUID hogar, UUID usuario, Rol rol) {
        // Memberships created one after another keep their order even within the same millisecond.
        Timestamp creado = new Timestamp(System.currentTimeMillis());
        creado.setNanos(creado.getNanos() + (int) (System.nanoTime() % 1_000_000));
        jdbc.update(
                "insert into membresias (id, hogar_id, usuario_id, rol, creado_en, actualizado_en)"
                        + " values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                hogar,
                usuario,
                rol.name(),
                creado,
                creado);
    }
}
