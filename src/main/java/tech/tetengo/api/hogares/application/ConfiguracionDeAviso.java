package tech.tetengo.api.hogares.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.hogares.OrdenDeAviso;
import tech.tetengo.api.hogares.application.port.HogarRepository;
import tech.tetengo.api.hogares.application.port.MembresiaRepository;
import tech.tetengo.api.hogares.domain.HogarError;
import tech.tetengo.api.hogares.domain.model.Hogar;
import tech.tetengo.api.hogares.domain.model.Membresia;
import tech.tetengo.api.shared.domain.exception.ErrorComun;
import tech.tetengo.api.shared.domain.exception.ErrorDeNegocio;

/**
 * US-10: the alert order of the household. Members read it; the owner changes it. Contacts that are
 * no longer members fall back to the defaults: the owner as primary and no secondary (CA-10.4).
 */
@Service
public class ConfiguracionDeAviso implements OrdenDeAviso {

    private final HogarRepository hogares;
    private final MembresiaRepository membresias;
    private final MiembroActual miembroActual;

    public ConfiguracionDeAviso(HogarRepository hogares, MembresiaRepository membresias, MiembroActual miembroActual) {
        this.hogares = hogares;
        this.membresias = membresias;
        this.miembroActual = miembroActual;
    }

    @Transactional(readOnly = true)
    public Configuracion consultar(UUID usuarioId) {
        return de(miembroActual.de(usuarioId).getHogarId());
    }

    /** CA-10.1, CA-10.2. */
    @Transactional
    public Configuracion configurar(UUID usuarioId, UUID principalId, UUID secundarioId, int esperaMinutos) {
        Membresia propia = miembroActual.de(usuarioId);
        if (!propia.esTitular()) {
            throw new ErrorDeNegocio(ErrorComun.SOLO_TITULAR);
        }
        Set<UUID> miembros = miembros(propia.getHogarId());
        if (!miembros.contains(principalId) || (secundarioId != null && !miembros.contains(secundarioId))) {
            throw new ErrorDeNegocio(HogarError.CONTACTO_NO_ES_FAMILIAR);
        }
        Hogar hogar = hogar(propia.getHogarId());
        hogar.configurarAviso(principalId, secundarioId, esperaMinutos);
        hogares.guardar(hogar);
        return de(hogar.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Configuracion de(UUID hogarId) {
        Hogar hogar = hogar(hogarId);
        Set<UUID> miembros = miembros(hogarId);
        UUID principal =
                miembros.contains(hogar.getAvisoPrincipalId()) ? hogar.getAvisoPrincipalId() : hogar.getTitularId();
        UUID secundario = miembros.contains(hogar.getAvisoSecundarioId())
                        && !hogar.getAvisoSecundarioId().equals(principal)
                ? hogar.getAvisoSecundarioId()
                : null;
        return new Configuracion(principal, secundario, hogar.getEsperaMinutos());
    }

    private Hogar hogar(UUID hogarId) {
        return hogares.buscar(hogarId).orElseThrow(() -> new ErrorDeNegocio(HogarError.SIN_MEMBRESIA));
    }

    private Set<UUID> miembros(UUID hogarId) {
        List<Membresia> todas = membresias.delHogar(hogarId);
        return todas.stream().map(Membresia::getUsuarioId).collect(Collectors.toSet());
    }
}
