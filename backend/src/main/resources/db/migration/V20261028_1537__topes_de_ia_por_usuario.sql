-- B-M1 (auditoría del 28 oct): el presupuesto de IA se reserva antes de llamar al modelo y cada persona tiene
-- su propio techo diario (ADR-0010). `micros` = gasto ya registrado + reservas de las llamadas en curso; la fila
-- del día nace con el gasto de `ai_usage_daily` (o de `ai_usage` de esa persona) y se bloquea con FOR UPDATE.
create table ai_budget_global (
    day    date primary key, -- día en America/Bogota
    micros bigint not null default 0
);

create table ai_budget_user (
    day     date not null,
    user_id uuid not null references users (id) on delete cascade,
    micros  bigint not null default 0,
    primary key (day, user_id)
);

-- Semilla de `ai_budget_user`: gasto de una persona en el día.
create index ai_usage_user_day_idx on ai_usage (user_id, day);

-- Topes diarios internos por persona (no son cupos del contrato ni salen en GET /v1/quota): reenvíos de las
-- respuestas del gemelo y moderaciones con IA. Se incrementan con un solo INSERT ... ON CONFLICT condicional.
create table daily_counters (
    user_id uuid not null references users (id) on delete cascade,
    day     date not null, -- día en America/Bogota
    kind    text not null check (kind in ('TWIN_ANSWERS', 'MODERATION')),
    count   int not null default 0,
    primary key (user_id, day, kind)
);
