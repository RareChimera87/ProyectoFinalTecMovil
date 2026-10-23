-- E6.1 · Intentos de canje de códigos de invitación (límite diario por persona).
-- Cada `POST /v1/invites/redeem` deja una fila antes de buscar el código, exista o no. Con el límite de
-- intentos por día nadie puede recorrer el espacio de códigos para descubrir quién tiene cuál (y su nombre)
-- ni llenar de avisos a desconocidos. Solo se guarda quién y cuándo; las filas viejas se borran al registrar
-- uno nuevo y se van con la cuenta (on delete cascade).

create table invite_redeem_attempts (
    id         uuid        primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete cascade,
    created_at timestamptz not null default now()
);

create index invite_redeem_attempts_user_idx on invite_redeem_attempts (user_id, created_at);
