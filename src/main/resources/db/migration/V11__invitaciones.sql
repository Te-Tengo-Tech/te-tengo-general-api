-- US-08: invitations to join a household. Global table, like instalaciones: the invitee accepts
-- through the link before belonging to any household, so the token is what identifies the household.
create table invitaciones (
    id              uuid primary key,
    hogar_id        uuid         not null references hogares (id),
    correo          varchar(254) not null,
    token_hash      varchar(64)  not null,
    invitado_por    uuid         not null,
    expira_en       timestamptz  not null,
    aceptada_en     timestamptz,
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null
);

create unique index uq_invitaciones_token on invitaciones (token_hash);
create index idx_invitaciones_hogar on invitaciones (hogar_id);
