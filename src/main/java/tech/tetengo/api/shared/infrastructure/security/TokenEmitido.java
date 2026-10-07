package tech.tetengo.api.shared.infrastructure.security;

import java.time.Instant;

public record TokenEmitido(String valor, Instant expiraEn) {}
