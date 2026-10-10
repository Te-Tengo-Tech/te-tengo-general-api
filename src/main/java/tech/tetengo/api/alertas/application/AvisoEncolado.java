package tech.tetengo.api.alertas.application;

import java.util.UUID;

/**
 * A push notice was queued ({@code AvisoPendiente}) or became due again: its first attempt goes out
 * once the transaction that queued it commits ({@link DespachoDeAvisos}). Internal to {@code alertas}.
 */
public record AvisoEncolado(UUID hogarId, UUID avisoId) {}
