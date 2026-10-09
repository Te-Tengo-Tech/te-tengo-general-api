package tech.tetengo.api.historial.domain.model;

/** CA-27.3: how a type of alert changed against the previous week. */
public enum Tendencia {
    AUMENTO,
    IGUAL,
    DISMINUCION;

    public static Tendencia comparar(long estaSemana, long anterior) {
        if (estaSemana > anterior) {
            return AUMENTO;
        }
        return estaSemana < anterior ? DISMINUCION : IGUAL;
    }
}
