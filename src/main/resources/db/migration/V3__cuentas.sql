-- Accounts of family members and caregivers (US-01). Global table: an account can belong to several households.
create table cuentas (
    id                  uuid primary key,
    correo              varchar(254) not null,
    nombre              varchar(120) not null,
    contrasena_cifrada  varchar(100) not null,
    creado_en           timestamptz  not null,
    actualizado_en      timestamptz  not null
);

-- E-mails are stored normalized (trimmed, lower case), so a plain unique index is enough.
create unique index uq_cuentas_correo on cuentas (correo);
