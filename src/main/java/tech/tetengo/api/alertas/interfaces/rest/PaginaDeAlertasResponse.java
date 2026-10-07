package tech.tetengo.api.alertas.interfaces.rest;

import java.util.List;

record PaginaDeAlertasResponse(List<AlertaResponse> elementos, long total) {}
