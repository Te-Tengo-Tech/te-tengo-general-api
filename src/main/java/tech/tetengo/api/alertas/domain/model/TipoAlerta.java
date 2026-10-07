package tech.tetengo.api.alertas.domain.model;

public enum TipoAlerta {
    CAIDA(Severidad.ALTA),
    MOVIMIENTO_INESTABLE(Severidad.MEDIA);

    private final Severidad severidad;

    TipoAlerta(Severidad severidad) {
        this.severidad = severidad;
    }

    /** CA-17.1: unstable movement is a medium-severity alert; a fall is high. */
    public Severidad severidad() {
        return severidad;
    }
}
