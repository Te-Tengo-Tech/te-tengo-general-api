package tech.tetengo.api.camaras.interfaces.rest;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.tetengo.api.camaras.application.ListarCamaras;
import tech.tetengo.api.camaras.application.RenombrarCamara;
import tech.tetengo.api.shared.infrastructure.web.ApiVersioning;

@RestController
@RequestMapping(ApiVersioning.BASE + "/camaras")
class CamaraController {

    private final ListarCamaras listarCamaras;
    private final RenombrarCamara renombrarCamara;

    CamaraController(ListarCamaras listarCamaras, RenombrarCamara renombrarCamara) {
        this.listarCamaras = listarCamaras;
        this.renombrarCamara = renombrarCamara;
    }

    @GetMapping(version = ApiVersioning.V1)
    List<CamaraResponse> listar() {
        return listarCamaras.ejecutar().stream().map(CamaraMapper::aRespuesta).toList();
    }

    @PatchMapping(path = "/{id}", version = ApiVersioning.V1)
    CamaraResponse renombrar(@PathVariable UUID id, @Valid @RequestBody RenombrarCamaraRequest pedido) {
        return CamaraMapper.aRespuesta(renombrarCamara.ejecutar(id, pedido.nombreHabitacion()));
    }
}
