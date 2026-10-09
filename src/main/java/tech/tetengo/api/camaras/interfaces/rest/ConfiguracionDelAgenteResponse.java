package tech.tetengo.api.camaras.interfaces.rest;

import java.math.BigDecimal;
import java.util.Map;

record ConfiguracionDelAgenteResponse(String versionAgente, Map<String, BigDecimal> umbrales) {}
