-- US-41 (CA-41.6): «Ver todas» watches several cameras at once and is one entry of the access log.
-- A mosaic belongs to the member who opened it; the live view sessions opened from it carry its id,
-- and the access log groups them into one entry with the rooms seen.
create table mosaicos_vista_en_vivo (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    usuario_id      uuid        not null,
    inicio          timestamptz not null,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create index idx_mosaicos_vista_en_vivo_hogar on mosaicos_vista_en_vivo (hogar_id);

alter table accesos_vista_en_vivo
    add column mosaico_id uuid references mosaicos_vista_en_vivo (id);

create index idx_accesos_vista_en_vivo_mosaico on accesos_vista_en_vivo (mosaico_id) where mosaico_id is not null;
