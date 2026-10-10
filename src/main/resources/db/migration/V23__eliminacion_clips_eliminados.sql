-- Hotfix 0.3.4: the app reads the deletion status from GET /api/hogar (eliminacion) instead of
-- relying on the push DATOS_ELIMINADOS only. clips_eliminados: recordings the deletion removed,
-- set when it completes. Completed deletions marked their clips with the same instant.
alter table eliminaciones_de_grabaciones add column clips_eliminados bigint;

update eliminaciones_de_grabaciones e
set clips_eliminados = (
    select count(*) from alertas a where a.hogar_id = e.hogar_id and a.clip_eliminado_en = e.completada_en)
where e.completada_en is not null;

create index idx_eliminaciones_hogar_solicitada on eliminaciones_de_grabaciones (hogar_id, solicitada_en desc);
