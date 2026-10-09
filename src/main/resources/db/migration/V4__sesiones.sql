-- US-02: consecutive failed sign-ins lock the account for 15 minutes (CA-02.3).
alter table cuentas
    add column intentos_fallidos integer not null default 0,
    add column bloqueada_hasta   timestamptz;

-- Sessions with their refresh token (only its SHA-256 is stored). Global table: a session may have no
-- household yet. hogar_id has no foreign key because hogares belongs to another module.
create table sesiones (
    id                   uuid primary key,
    usuario_id           uuid        not null references cuentas (id),
    hogar_id             uuid,
    rol                  varchar(20),
    token_refresco_hash  varchar(64) not null,
    expira_en            timestamptz not null,
    cerrada_en           timestamptz,
    creado_en            timestamptz not null,
    actualizado_en       timestamptz not null
);

create unique index uq_sesiones_token_refresco on sesiones (token_refresco_hash);
create index idx_sesiones_usuario on sesiones (usuario_id);
