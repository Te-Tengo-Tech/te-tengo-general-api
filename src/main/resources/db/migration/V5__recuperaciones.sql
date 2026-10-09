-- US-03: password recovery links. Only the SHA-256 of the link token is stored; links last 30 minutes.
create table recuperaciones (
    id              uuid primary key,
    usuario_id      uuid        not null references cuentas (id),
    token_hash      varchar(64) not null,
    expira_en       timestamptz not null,
    usada_en        timestamptz,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create unique index uq_recuperaciones_token on recuperaciones (token_hash);
create index idx_recuperaciones_usuario on recuperaciones (usuario_id);
