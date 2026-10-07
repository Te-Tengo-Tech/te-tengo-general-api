-- US-04: the household stores its older adult (one per account, CA-04.2) and its owner.
alter table hogares
    add column titular_id                uuid         not null,
    add column adulto_mayor_nombre       varchar(120) not null,
    add column adulto_mayor_direccion    varchar(250) not null,
    add column adulto_mayor_convivencia  varchar(20)  not null;

-- Household–user memberships. Global table (MULTITENANCY.md): a user can belong to several
-- households. usuario_id has no foreign key because accounts belong to the cuentas module.
create table membresias (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    usuario_id      uuid        not null,
    rol             varchar(20) not null,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create unique index uq_membresias_hogar_usuario on membresias (hogar_id, usuario_id);
create index idx_membresias_usuario on membresias (usuario_id);
-- CA-04.2: an account owns (manages) a single older adult.
create unique index uq_membresias_un_titular on membresias (usuario_id) where rol = 'TITULAR';
