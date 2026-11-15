-- API contract §7, ADR 0014: the Android app draws the notice itself from a data-only push, which
-- FCM does not collapse while the phone is offline. Opt-in per device; existing devices keep
-- notification messages until their app registers again with avisoEnLaApp = true.
alter table dispositivos add column aviso_en_la_app boolean not null default false;
