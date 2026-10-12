package tech.tetengo.api.versiones.domain.model;

import java.util.List;

/**
 * A channel's manifest as the release workflow wrote it, not checked yet (ADR 0012):
 * {@code {"version", "url", "sha256", "tamanoBytes", "publicadaEn", "notas"}}. {@link
 * VersionPublicada#de} checks it.
 */
public record EntradaDeManifiesto(
        String version, String url, String sha256, Long tamanoBytes, String publicadaEn, List<String> notas) {

    public EntradaDeManifiesto {
        notas = notas == null
                ? List.of()
                : notas.stream().filter(n -> n != null).toList();
    }
}
