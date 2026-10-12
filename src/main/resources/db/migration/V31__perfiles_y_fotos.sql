-- API contract §13: profiles and photos. A member's phone and relationship («Hija», «Vecina»), and the
-- object keys of the stored photos (JPEG, at most 512 x 512). Null: no photo, the app shows initials.
alter table cuentas
    add column telefono    varchar(20),
    add column parentesco  varchar(40),
    add column foto_clave  varchar(200);

alter table hogares
    add column adulto_mayor_foto_clave  varchar(200);

-- Pre-signed uploads waiting for their confirmation. Global table: a user without a household can
-- upload their own photo. hogar_id is only set for the older adult's photo, to check at confirmation
-- that the caller still acts in the same household. Rows are deleted on confirmation, or by the
-- cleanup job 24 hours after they were asked for (with their object).
create table subidas_de_fotos (
    id              uuid primary key,
    usuario_id      uuid         not null,
    destino         varchar(20)  not null,
    hogar_id        uuid,
    tipo            varchar(20)  not null,
    tamano_bytes    bigint       not null,
    clave           varchar(200) not null,
    pedida_en       timestamptz  not null,
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null
);

create index idx_subidas_de_fotos_pedida_en on subidas_de_fotos (pedida_en);
