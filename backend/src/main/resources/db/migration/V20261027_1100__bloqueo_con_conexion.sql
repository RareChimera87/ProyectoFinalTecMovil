-- E8.7: al bloquear se borran las aristas CONNECTED; esta marca conserva si la persona bloqueada
-- era una conexión con nombre, para presentarla así en 'Personas bloqueadas'.
alter table blocks add column was_connected boolean not null default false;
