package tech.tetengo.api.camaras;

import java.util.UUID;

/**
 * CA-29.6: the owner chose «Buscar de nuevo» in the app: the PC looks for its webcams again and
 * reports them ({@code PUT /api/agente/pc/camaras/inventario}).
 */
public record BusquedaDeCamarasPedida(UUID hogarId, UUID pcId) {}
