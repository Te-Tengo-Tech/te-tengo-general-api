-- US-29: the household's rooms («ambientes»; the apps label them «habitación»). A camera belongs to one
-- PC and one room; a room may hold several cameras in the schema, while the use cases allow one for now
-- (CA-29.3). camaras.nombre_habitacion stays and always holds the room's name, so every existing
-- reader (alerts, notices, the agent's capture state) keeps working. Additive only.

create table ambientes (
    id                  uuid primary key,
    hogar_id            uuid        not null references hogares (id),
    nombre              varchar(40) not null,
    -- lowercase name with single spaces: the uniqueness key within the household
    nombre_normalizado  varchar(40) not null,
    eliminado_en        timestamptz,
    creado_en           timestamptz not null,
    actualizado_en      timestamptz not null
);

create unique index uq_ambientes_nombre on ambientes (hogar_id, nombre_normalizado) where eliminado_en is null;
create index idx_ambientes_hogar on ambientes (hogar_id);

alter table camaras
    add column ambiente_id         uuid references ambientes (id),
    -- the operating system's unique id of the webcam (CA-29.4); null for installation cameras
    add column dispositivo_id      varchar(300),
    add column nombre_dispositivo  varchar(100);

create index idx_camaras_ambiente on camaras (ambiente_id) where retirada_en is null;
create unique index uq_camaras_pc_dispositivo on camaras (pc_id, dispositivo_id) where dispositivo_id is not null;

-- Existing data: one room per household and distinct room name of its cameras in use (cameras that
-- share a name share the room).
insert into ambientes (id, hogar_id, nombre, nombre_normalizado, creado_en, actualizado_en)
select gen_random_uuid(),
       hogar_id,
       min(regexp_replace(btrim(nombre_habitacion), '\s+', ' ', 'g')),
       lower(regexp_replace(btrim(nombre_habitacion), '\s+', ' ', 'g')),
       min(creado_en),
       now()
from camaras
where retirada_en is null
group by hogar_id, lower(regexp_replace(btrim(nombre_habitacion), '\s+', ' ', 'g'));

update camaras c
set ambiente_id = a.id
from ambientes a
where a.hogar_id = c.hogar_id
  and a.nombre_normalizado = lower(regexp_replace(btrim(c.nombre_habitacion), '\s+', ' ', 'g'))
  and c.retirada_en is null;
