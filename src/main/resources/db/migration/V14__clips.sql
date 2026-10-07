-- US-18: clip of the event (6 s before and 6 s after), stored in object storage under clip_clave.
alter table alertas
    add column clip_clave        varchar(300),
    add column clip_subido       boolean not null default false,
    add column clip_eliminado_en timestamptz;
