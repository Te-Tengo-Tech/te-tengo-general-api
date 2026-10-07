-- API contract: the older adult's age («Edad» in the prototype's profile form) and the phone that
-- «Llamar a Rosa» dials. Both nullable: households registered before have neither until the owner
-- saves the profile again; the API requires the age on every write.
alter table hogares
    add column adulto_mayor_edad      integer,
    add column adulto_mayor_telefono  varchar(20);
