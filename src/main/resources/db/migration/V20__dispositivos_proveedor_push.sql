-- Push providers (docs/NOTIFICATIONS.md). A device whose token the push service rejects is deactivated
-- until the phone registers it again; referencia_push is the provider's address of the device (the
-- Amazon SNS platform endpoint ARN).
alter table dispositivos
    add column activo          boolean       not null default true,
    add column referencia_push varchar(1024);
