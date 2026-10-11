package tech.tetengo.api.camaras.application.port;

import java.util.Optional;
import java.util.UUID;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc;

/** Refresh tokens of the PCs (global table: the token request carries no JWT). */
public interface TokenDeRenovacionRepository {

    TokenDeRenovacionDePc guardar(TokenDeRenovacionDePc token);

    Optional<TokenDeRenovacionDePc> porHuella(String huella);

    Optional<TokenDeRenovacionDePc> porHuellaParaActualizar(String huella);

    /** RFC 9700 §4.14.2 and US-31: every token of the PC stops working. */
    void revocarTodosDe(UUID pcId, java.time.Instant ahora);
}
