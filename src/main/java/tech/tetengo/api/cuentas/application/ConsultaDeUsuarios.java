package tech.tetengo.api.cuentas.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.tetengo.api.cuentas.DirectorioDeUsuarios;
import tech.tetengo.api.cuentas.application.port.CuentaRepository;
import tech.tetengo.api.cuentas.domain.model.Cuenta;

@Service
public class ConsultaDeUsuarios implements DirectorioDeUsuarios {

    private final CuentaRepository cuentas;

    public ConsultaDeUsuarios(CuentaRepository cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscar(UUID id) {
        return cuentas.buscar(id).map(ConsultaDeUsuarios::aUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return cuentas.buscarPorCorreo(Cuenta.normalizarCorreo(correo)).map(ConsultaDeUsuarios::aUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Usuario> buscarTodos(Collection<UUID> ids) {
        return cuentas.buscarTodos(ids).stream()
                .map(ConsultaDeUsuarios::aUsuario)
                .collect(Collectors.toMap(Usuario::id, Function.identity()));
    }

    private static Usuario aUsuario(Cuenta cuenta) {
        return new Usuario(cuenta.getId(), cuenta.getNombre(), cuenta.getCorreo());
    }
}
