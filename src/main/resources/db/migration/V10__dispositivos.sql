-- Push devices of family members (API contract §7). Global table: a device belongs to a user, who
-- may be a member of several households. usuario_id has no foreign key (accounts are another module).
create table dispositivos (
    id              uuid primary key,
    token_push      varchar(512) not null,
    usuario_id      uuid         not null,
    plataforma      varchar(10)  not null,
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null
);

create unique index uq_dispositivos_token on dispositivos (token_push);
create index idx_dispositivos_usuario on dispositivos (usuario_id);

create index idx_camaras_conexion on camaras (estado_conexion, ultima_senal);
