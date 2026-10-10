-- Hotfix 0.3.1, delivery of push notices (docs/NOTIFICATIONS.md).
-- alertas.estado_aviso: whether the family got the alert's notice (ENVIANDO, ENTREGADO,
-- REINTENTANDO, NO_ENTREGADO). Earlier alerts: delivered when notificada_en is set, otherwise not.
alter table alertas add column estado_aviso varchar(20) not null default 'NO_ENTREGADO';
update alertas set estado_aviso = 'ENTREGADO' where notificada_en is not null;
alter table alertas alter column estado_aviso drop default;

-- avisos_pendientes is now the outbox of every notice; vence_en is when it stops being retried.
alter table avisos_pendientes add column vence_en timestamptz;
update avisos_pendientes set vence_en = proximo_intento + interval '5 minutes';
alter table avisos_pendientes alter column vence_en set not null;

-- dispositivos.visto_en: last registration (the app registers on every start and resume);
-- desactivado_en: when the push service said the token no longer exists.
alter table dispositivos
    add column visto_en       timestamptz,
    add column desactivado_en timestamptz;
update dispositivos set visto_en = actualizado_en;
update dispositivos set desactivado_en = actualizado_en where not activo;
alter table dispositivos alter column visto_en set not null;
