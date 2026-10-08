/**
 * Camera pauses with automatic resume, live view through MediaMTX (sessions, the agent's control channel
 * and MediaMTX's authorization) and the live view access log. Stories US-22 to US-24.
 *
 * <p>Public API: the {@code PausaFinalizada} event and the {@code AlertasDeCamara} SPI that
 * {@code alertas} implements. It works on cameras through {@code CamarasDelHogar} and listens to
 * {@code ConsentimientoRevocado} of {@code hogares} to stop live view.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Monitoreo")
package tech.tetengo.api.monitoreo;
