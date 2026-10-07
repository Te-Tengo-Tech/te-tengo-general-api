/**
 * Events sent by the household agent, alerts and their clips, alert state (attended or false alarm),
 * escalation and every push notice of the system. Stories US-11 to US-21, plus US-25 and US-26 for
 * the alert history and clips.
 *
 * <p>Public API: {@code ConteoDeAlertas} and {@code RetencionDeClips} for {@code historial}. It listens
 * to {@code camaras}, {@code hogares} and {@code monitoreo} events to send their push notices.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Alertas")
package tech.tetengo.api.alertas;
