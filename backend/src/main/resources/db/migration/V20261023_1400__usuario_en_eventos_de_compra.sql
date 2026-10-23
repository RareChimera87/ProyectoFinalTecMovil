-- E7.1 (revisión profunda): la purga de E8.3 redacta los eventos de compra de TODOS los tokens de la persona,
-- no solo el vigente. Para encontrarlos, cada evento se liga a la cuenta cuando se conoce (sin llave foránea:
-- la purga lo deja en null junto con el token).
alter table purchase_events add column user_id uuid;
create index purchase_events_user_idx on purchase_events (user_id) where user_id is not null;

update purchase_events e
set user_id = en.user_id
from entitlements en
where e.purchase_token = en.purchase_token;
