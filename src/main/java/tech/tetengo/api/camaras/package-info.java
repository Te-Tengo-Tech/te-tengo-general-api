/**
 * Household cameras: registration when the agent starts, room name, connection status, capture state
 * (consent and pauses) and detection reliability. Stories US-06, US-07 and the camera side of US-05,
 * US-15 and US-22.
 *
 * <p>This is the <b>reference slice</b>: copy its structure for the other modules. Public API:
 * {@code CamarasDelHogar} and the events {@code CamaraDesconectada} and {@code CamaraReconectada}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cámaras")
package tech.tetengo.api.camaras;
