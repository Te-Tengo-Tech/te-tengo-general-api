/**
 * Movement data (EP08; change request «PCs, cámaras y datos de movimiento», pending approval): the
 * separate, optional consent to store the older adult's movements (US-35), the body landmarks the PC
 * uploads to object storage every 5 minutes with an index and a cloud retention the owner chooses
 * (US-37, ADR 0010), and the replay of a camera's movements with its access log (US-39).
 *
 * <p>Public API: {@code DatosDeMovimiento} (consent in force, stored files, deletion of everything for
 * a monitoring consent revocation) and the events {@code ConsentimientoDeMovimientosOtorgado},
 * {@code ConsentimientoDeMovimientosRevocado}, {@code RetencionDeMovimientosCambiada} and
 * {@code MovimientosEliminados}. It reads cameras through {@code CamarasDelHogar} and listens to
 * {@code ConsentimientoRevocado} of {@code hogares}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Movimientos")
package tech.tetengo.api.movimientos;
