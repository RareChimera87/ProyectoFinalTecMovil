-- E6.1 · Canjes de códigos de invitación.
-- Quien canjea el código de otra persona (`redeemer_id`) pide conectar con su dueño (`owner_id`) en un
-- modo que los dos tienen activo: la arista queda PENDING hasta que el dueño acepta (CONNECTED). La fila
-- recuerda quién canjeó a quién, porque la arista de `edges` es simétrica (una sola por par y modo) y no
-- dice quién debe aceptar. Un canje repetido es idempotente (clave primaria). Al purgar una cuenta las
-- filas se van con ella (on delete cascade).

create table invite_redemptions (
    owner_id    uuid        not null references users (id) on delete cascade,
    redeemer_id uuid        not null references users (id) on delete cascade,
    mode        text        not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    status      text        not null default 'PENDING' check (status in ('PENDING', 'CONNECTED')),
    created_at  timestamptz not null default now(),
    accepted_at timestamptz,
    primary key (owner_id, redeemer_id, mode),
    check (owner_id <> redeemer_id)
);

-- Lista de canjes por aceptar del dueño (GET /v1/invites/me) y búsqueda inversa al canjear.
create index invite_redemptions_owner_pending_idx on invite_redemptions (owner_id, created_at desc) where status = 'PENDING';
create index invite_redemptions_redeemer_idx on invite_redemptions (redeemer_id);
