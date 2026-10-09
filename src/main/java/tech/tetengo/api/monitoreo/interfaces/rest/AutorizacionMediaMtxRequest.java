package tech.tetengo.api.monitoreo.interfaces.rest;

/** The body MediaMTX posts with {@code authMethod: http}; other fields are ignored. */
record AutorizacionMediaMtxRequest(
        String user,
        String password,
        String token,
        String ip,
        String action,
        String path,
        String protocol,
        String id,
        String query) {}
