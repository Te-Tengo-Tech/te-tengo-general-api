package tech.tetengo.api.monitoreo.interfaces.rest;

/** {@code duracion}: {@code MIN_30}, {@code HORA_1}, {@code HORAS_2} or {@code HASTA_MANANA}. */
record PausarCamaraRequest(String duracion) {}
