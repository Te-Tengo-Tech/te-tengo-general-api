-- Registro de publicación de eventos de Spring Modulith (entrega garantizada entre módulos).
-- Tabla global: los eventos no pertenecen a un hogar. Columnas de JpaEventPublication.
create table event_publication (
    id                     uuid        not null primary key,
    listener_id            text        not null,
    event_type             text        not null,
    serialized_event       text        not null,
    publication_date       timestamptz not null,
    completion_date        timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts    integer     not null default 0,
    status                 varchar(16) not null default 'PROCESSING'
);

create index idx_event_publication_completion_date on event_publication (completion_date);
create index idx_event_publication_by_listener_serialized on event_publication (listener_id, serialized_event);
