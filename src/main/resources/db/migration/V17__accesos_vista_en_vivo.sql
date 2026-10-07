-- US-23 and US-24: live view sessions, which are also the access log (who, when, how long).
-- token_hash is the SHA-256 of the one-time token in the stream URL.
create table accesos_vista_en_vivo (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    camara_id       uuid        not null references camaras (id),
    usuario_id      uuid        not null,
    alerta_id       uuid,
    inicio          timestamptz not null,
    expira_en       timestamptz not null,
    conectada_en    timestamptz,
    fin             timestamptz,
    token_hash      varchar(64) not null,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create index idx_accesos_vista_en_vivo_hogar on accesos_vista_en_vivo (hogar_id, inicio desc);
create unique index uq_accesos_vista_en_vivo_token on accesos_vista_en_vivo (token_hash);
