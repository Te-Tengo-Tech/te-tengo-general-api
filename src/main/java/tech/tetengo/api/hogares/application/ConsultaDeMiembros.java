package tech.tetengo.api.hogares.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.MiembrosDelHogar;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;

@Service
public class ConsultaDeMiembros implements MiembrosDelHogar {

    private final MembresiaRepository membresias;

    public ConsultaDeMiembros(MembresiaRepository membresias) {
        this.membresias = membresias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Miembro> de(UUID hogarId) {
        return membresias.delHogar(hogarId).stream()
                .map(m -> new Miembro(m.getUsuarioId(), m.getRol()))
                .toList();
    }
}
