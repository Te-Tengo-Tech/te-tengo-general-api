package tech.tetengo.api.camaras.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tech.tetengo.api.camaras.application.port.TokenDeRenovacionRepository;
import tech.tetengo.api.camaras.domain.model.TokenDeRenovacionDePc;

@Repository
class TokenDeRenovacionRepositoryAdapter implements TokenDeRenovacionRepository {

    private final TokenDeRenovacionJpaRepository jpa;

    TokenDeRenovacionRepositoryAdapter(TokenDeRenovacionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public TokenDeRenovacionDePc guardar(TokenDeRenovacionDePc token) {
        return jpa.save(token);
    }

    @Override
    public Optional<TokenDeRenovacionDePc> porHuella(String huella) {
        return jpa.findByTokenHash(huella);
    }

    @Override
    public Optional<TokenDeRenovacionDePc> porHuellaParaActualizar(String huella) {
        return jpa.findByTokenHashForUpdate(huella);
    }

    @Override
    public void revocarTodosDe(UUID pcId, Instant ahora) {
        jpa.revocarTodosDe(pcId, ahora);
    }
}
