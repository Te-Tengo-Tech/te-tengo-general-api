package tech.tetengo.api.movimientos.application;

/**
 * What the PC needs to know before storing movements (CA-35.3: without the consent it stores nothing,
 * on the PC or in the cloud; CA-37.1: the cloud only with a retention chosen).
 *
 * @param guardarEnLaPc the consent is in force: the PC may store movements locally (US-38)
 * @param guardarEnLaNube it may also upload them (US-37)
 * @param retencionDias the cloud retention, null when off
 */
public record EstadoDeMovimientos(
        boolean consentimientoVigente,
        boolean guardarEnLaPc,
        boolean guardarEnLaNube,
        Integer retencionDias,
        String versionTextoVigente) {}
