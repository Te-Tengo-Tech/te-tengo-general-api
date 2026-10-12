/**
 * Household cameras: registration when the agent starts, room name, connection status, capture state
 * (consent and pauses) and detection reliability. Stories US-06, US-07 and the camera side of US-05,
 * US-15 and US-22. Also the household's PCs and their link with the OAuth 2.0 device grant (US-28,
 * US-30, US-31; ADR 0009).
 *
 * <p>This is the <b>reference slice</b>: copy its structure for the other modules. Public API:
 * {@code CamarasDelHogar} and the events {@code CamaraDesconectada}, {@code CamaraReconectada},
 * {@code PcVinculada}, {@code PcRenombrada} and {@code PcDesvinculada}; it implements the
 * {@code ComprobadorDeAgentes} SPI of {@code shared}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cámaras")
package tech.tetengo.api.camaras;
