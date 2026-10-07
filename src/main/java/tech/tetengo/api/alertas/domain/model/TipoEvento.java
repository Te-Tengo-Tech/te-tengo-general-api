package tech.tetengo.api.alertas.domain.model;

import java.util.Arrays;
import java.util.Optional;

/** Event types of the validated classifier of the household agent (AGENT_CONTRACT.md). */
public enum TipoEvento {
    CAIDA("caida"),
    CAIDA_CONFIRMADA("caida_confirmada"),
    MOVIMIENTO_INESTABLE("movimiento_inestable"),
    RECUPERACION("recuperacion"),
    DETECCION_NO_CONFIABLE("deteccion_no_confiable");

    private final String valor;

    TipoEvento(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    public static Optional<TipoEvento> desde(String valor) {
        return Arrays.stream(values()).filter(t -> t.valor.equals(valor)).findFirst();
    }
}
