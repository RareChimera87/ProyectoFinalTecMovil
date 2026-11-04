-- ParcheU · Esquema v1 (E0.4). Fuente: docs/arquitectura.md §4 y ADR-0003.
-- Convenciones: uuid con gen_random_uuid(), timestamptz, enums como text con CHECK.
-- Las tablas que cuelgan de un usuario y solo contienen datos suyos usan ON DELETE CASCADE
-- (purga de demo y de cuentas); las que deben sobrevivir al usuario (consents, compras,
-- simulaciones compartidas, reportes) no tienen cascada.
-- Nuevas migraciones: V<yyyyMMdd>_<HHmm>__<descripcion>.sql (ADR-0011).

-- =====================================================================
-- Identidad
-- =====================================================================

create table universities (
    id         smallint primary key,
    short_name text not null unique,
    name       text not null,
    active     boolean not null default true
);

create table interests (
    slug  text primary key,
    label text not null,
    icon  text not null
);

create table users (
    id                  uuid primary key default gen_random_uuid(),
    firebase_uid        text not null unique,
    email               text,
    display_name        text,
    photo_url           text,
    career              text,
    university_id       smallint references universities (id),
    -- 0..5 slugs del catálogo `interests` (se valida en el servicio: Postgres no admite FK en arreglos).
    interests           text[] not null default '{}' check (cardinality(interests) <= 5),
    birth_date          date,
    age_verified_at     timestamptz,
    underage_at         timestamptz,
    pareja_confirmed_at timestamptz,
    visibility          text not null default 'ANONYMOUS' check (visibility in ('NAME', 'ANONYMOUS', 'HIDDEN')),
    is_demo             boolean not null default false,
    is_test             boolean not null default false,
    suspended_until     timestamptz,
    deleted_at          timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create table user_modes (
    user_id    uuid not null references users (id) on delete cascade,
    mode       text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    enabled    boolean not null default false,
    enabled_at timestamptz,
    primary key (user_id, mode)
);

-- Solo agrega filas (ADR-0017): el estado vigente de cada tipo es la última fila.
create table consents (
    id           bigserial primary key,
    user_id      uuid not null references users (id),
    kind         text not null check (kind in ('TERMS', 'PRIVACY',
                     'CHAT_LEARNING_AMISTAD', 'CHAT_LEARNING_PAREJA', 'CHAT_LEARNING_ESTUDIO')),
    granted      boolean not null,
    text_version text not null,
    created_at   timestamptz not null default now()
);
create index consents_user_kind_created_idx on consents (user_id, kind, created_at desc);

-- La API y las migraciones usan el mismo usuario IAM (sa-api, dueño de las tablas), así que un
-- GRANT no basta para impedir UPDATE/DELETE: lo impide este trigger. La única excepción es la
-- purga por retención (E8.8, 5 años), que debe activar `set local parcheu.consents_purge = 'on'`
-- dentro de su transacción.
create function consents_append_only() returns trigger
    language plpgsql as
$$
begin
    if tg_op = 'DELETE' and coalesce(current_setting('parcheu.consents_purge', true), '') = 'on' then
        return old;
    end if;
    raise exception 'consents solo admite INSERT (operación % rechazada)', tg_op
        using errcode = 'insufficient_privilege';
end;
$$;

create trigger consents_no_update
    before update on consents
    for each row execute function consents_append_only();
create trigger consents_no_delete
    before delete on consents
    for each row execute function consents_append_only();
create trigger consents_no_truncate
    before truncate on consents
    for each statement execute function consents_append_only();

revoke update, delete, truncate on consents from public;

create table fcm_tokens (
    token      text primary key,
    user_id    uuid not null references users (id) on delete cascade,
    updated_at timestamptz not null default now()
);
create index fcm_tokens_user_idx on fcm_tokens (user_id);

-- =====================================================================
-- Gemelo
-- =====================================================================

create table questions (
    id       smallint primary key,
    ord      smallint not null unique,
    text     text not null,
    option_a text not null,
    option_b text not null,
    -- {"SOCIAL_ENERGY": 1.0, ...}: A empuja el rasgo hacia 1, B hacia 0, DEPENDS suma la mitad a ambos polos.
    weights  jsonb not null
);

create table twin_answers (
    user_id     uuid not null references users (id) on delete cascade,
    question_id smallint not null references questions (id),
    answer      text not null check (answer in ('A', 'B', 'DEPENDS')),
    answered_at timestamptz not null default now(),
    primary key (user_id, question_id)
);

create table twins (
    user_id         uuid primary key references users (id) on delete cascade,
    traits          real[] check (traits is null or cardinality(traits) = 6),
    persona_summary text,
    persona_ready   boolean not null default false,
    version         int not null default 1,
    level           int not null default 1,
    xp              int not null default 0,
    updated_at      timestamptz not null default now()
);

create table twin_versions (
    user_id         uuid not null references users (id) on delete cascade,
    version         int not null,
    traits          real[] check (traits is null or cardinality(traits) = 6),
    persona_summary text,
    created_at      timestamptz not null default now(),
    primary key (user_id, version)
);

create table twin_accuracy (
    user_id     uuid not null references users (id) on delete cascade,
    week        date not null, -- lunes de la semana en America/Bogota
    accuracy    smallint not null check (accuracy between 0 and 100),
    ratings     int not null default 0,
    predictions int not null default 0,
    primary key (user_id, week)
);

create table twin_memories (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null references users (id) on delete cascade,
    mode           text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    text           text not null,
    source_chat_id uuid,
    status         text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REJECTED')),
    created_at     timestamptz not null default now(),
    decided_at     timestamptz
);
create index twin_memories_user_mode_status_idx on twin_memories (user_id, mode, status);

create table twin_feedback (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references users (id) on delete cascade,
    kind          text not null check (kind in ('RATING', 'CONTROL')),
    simulation_id uuid,
    turn_index    smallint,
    question_id   smallint references questions (id),
    predicted     text,
    actual        text,
    rating        smallint,
    created_at    timestamptz not null default now()
);
create index twin_feedback_user_created_idx on twin_feedback (user_id, created_at);

create table twin_learning_cursor (
    user_id         uuid primary key references users (id) on delete cascade,
    last_message_at timestamptz
);

-- =====================================================================
-- Conocer
-- =====================================================================

create table scenarios (
    id        smallint primary key,
    mode      text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    slug      text not null unique,
    title     text not null,
    prompt    text not null,
    is_basic  boolean not null,
    timer_sec int not null check (timer_sec > 0),
    budget_cop int not null check (budget_cop >= 0),
    rubric    jsonb not null -- [{name, weight, description}]
);
create index scenarios_mode_idx on scenarios (mode, is_basic);

create table match_queue (
    user_id     uuid primary key references users (id) on delete cascade,
    mode        text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    priority    smallint not null default 0,
    enqueued_at timestamptz not null default now(),
    expires_at  timestamptz not null
);
create index match_queue_mode_priority_idx on match_queue (mode, priority desc, enqueued_at);

create table simulations (
    id             uuid primary key default gen_random_uuid(),
    mode           text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    user_a         uuid not null references users (id),
    user_b         uuid not null references users (id),
    requested_by   uuid references users (id),
    origin         text not null check (origin in ('RANDOM', 'PARCHE', 'INTRODUCTION', 'SCENARIO')),
    status         text not null default 'QUEUED' check (status in ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    progress       jsonb,
    score          smallint check (score between 0 and 100),
    report         jsonb,
    transcript     jsonb,
    twin_version_a int,
    twin_version_b int,
    cached_from    uuid references simulations (id),
    provider       text,
    model          text,
    tokens_in      int,
    tokens_out     int,
    cost_micros    bigint,
    error          text,
    created_at     timestamptz not null default now(),
    finished_at    timestamptz,
    check (user_a <> user_b)
);
create index simulations_user_a_idx on simulations (user_a);
create index simulations_user_b_idx on simulations (user_b);

create table report_unlocks (
    user_id       uuid not null references users (id) on delete cascade,
    simulation_id uuid not null references simulations (id),
    created_at    timestamptz not null default now(),
    primary key (user_id, simulation_id)
);

create table connection_decisions (
    simulation_id uuid not null references simulations (id),
    user_id       uuid not null references users (id) on delete cascade,
    connect       boolean not null,
    decided_at    timestamptz not null default now(),
    primary key (simulation_id, user_id)
);

-- =====================================================================
-- Chat (se crea antes de `matches`, que referencia `chats`)
-- =====================================================================

create table chats (
    id              uuid primary key default gen_random_uuid(),
    type            text not null check (type in ('DIRECT', 'SCENARIO')),
    mode            text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    scenario_id     smallint references scenarios (id),
    ends_at         timestamptz,
    status          text not null default 'OPEN' check (status in ('OPEN', 'CLOSED')),
    created_at      timestamptz not null default now(),
    last_message_at timestamptz,
    check (type <> 'SCENARIO' or scenario_id is not null)
);

create table chat_members (
    chat_id      uuid not null references chats (id) on delete cascade,
    user_id      uuid not null references users (id) on delete cascade,
    muted        boolean not null default false,
    last_read_at timestamptz,
    agreed_at    timestamptz,
    primary key (chat_id, user_id)
);
create index chat_members_user_idx on chat_members (user_id);

create table chat_messages (
    id         uuid primary key default gen_random_uuid(),
    chat_id    uuid not null references chats (id) on delete cascade,
    sender_id  uuid references users (id), -- null = mensaje del sistema o autor anonimizado
    text       text not null,
    learnable  boolean not null,
    hidden     boolean not null default false,
    client_id  text,
    created_at timestamptz not null default now(),
    unique (sender_id, client_id)
);
create index chat_messages_chat_created_idx on chat_messages (chat_id, created_at);
create index chat_messages_sender_created_idx on chat_messages (sender_id, created_at);

create table matches (
    id            uuid primary key default gen_random_uuid(),
    mode          text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    user_a        uuid not null references users (id) on delete cascade,
    user_b        uuid not null references users (id) on delete cascade,
    choice_a      text check (choice_a in ('SCENARIO', 'SIMULATION')),
    choice_b      text check (choice_b in ('SCENARIO', 'SIMULATION')),
    status        text not null default 'PENDING_CHOICE'
                      check (status in ('PENDING_CHOICE', 'SIMULATION', 'SCENARIO', 'EXPIRED')),
    simulation_id uuid references simulations (id),
    chat_id       uuid references chats (id),
    created_at    timestamptz not null default now(),
    decide_by     timestamptz not null,
    check (user_a <> user_b)
);
create index matches_user_a_idx on matches (user_a);
create index matches_user_b_idx on matches (user_b);

-- =====================================================================
-- Grafo (ADR-0003): una fila por par y modo, siempre con user_low < user_high.
-- =====================================================================

create table edges (
    user_low           uuid not null references users (id) on delete cascade,
    user_high          uuid not null references users (id) on delete cascade,
    mode               text not null check (mode in ('AMISTAD', 'PAREJA', 'ESTUDIO')),
    state              text not null check (state in ('CONNECTED', 'SIMULATED', 'PENDING')),
    affinity           smallint check (affinity between 0 and 100),
    last_simulation_id uuid references simulations (id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    connected_at       timestamptz,
    primary key (user_low, user_high, mode),
    constraint edges_user_order_chk check (user_low < user_high)
);
create index edges_user_high_idx on edges (user_high);

-- Vista en ambos sentidos: `me` es siempre el usuario desde el que se consulta.
create view edges_v as
select user_low as me, user_high as other, mode, state, affinity, last_simulation_id,
       created_at, updated_at, connected_at
from edges
union all
select user_high, user_low, mode, state, affinity, last_simulation_id,
       created_at, updated_at, connected_at
from edges;

create table communities (
    id         uuid primary key default gen_random_uuid(),
    week       date not null,
    name       text not null,
    stable_key text not null,
    created_at timestamptz not null default now(),
    unique (week, stable_key)
);

create table community_members (
    community_id uuid not null references communities (id) on delete cascade,
    user_id      uuid not null references users (id) on delete cascade,
    primary key (community_id, user_id)
);
create index community_members_user_idx on community_members (user_id);

-- =====================================================================
-- Social
-- =====================================================================

create table invites (
    code    text primary key check (code ~ '^[A-Z0-9]{4}-[0-9]{4}$'),
    user_id uuid not null unique references users (id) on delete cascade
);

create table introductions (
    id            uuid primary key default gen_random_uuid(),
    introducer_id uuid not null references users (id) on delete cascade,
    mode          text not null check (mode in ('AMISTAD', 'ESTUDIO')), -- Pareja nunca (ADR-0012)
    user_a        uuid not null references users (id) on delete cascade,
    user_b        uuid not null references users (id) on delete cascade,
    simulation_id uuid references simulations (id),
    a_response    text check (a_response in ('INTERESTED', 'DECLINED')),
    b_response    text check (b_response in ('INTERESTED', 'DECLINED')),
    status        text not null default 'SIMULATING'
                      check (status in ('SIMULATING', 'PROPOSED', 'CONNECTED', 'DECLINED', 'FAILED')),
    created_at    timestamptz not null default now(),
    check (user_a <> user_b)
);
create index introductions_user_a_idx on introductions (user_a);
create index introductions_user_b_idx on introductions (user_b);

create table intro_requests (
    id             uuid primary key default gen_random_uuid(),
    requester_id   uuid not null references users (id) on delete cascade,
    via_user_id    uuid not null references users (id) on delete cascade,
    target_user_id uuid not null references users (id) on delete cascade,
    status         text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    created_at     timestamptz not null default now()
);
create index intro_requests_via_idx on intro_requests (via_user_id, status);

-- =====================================================================
-- Notificaciones y cuotas
-- =====================================================================

create table notifications (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users (id) on delete cascade,
    kind       text not null, -- abierto a propósito: ver NotificationKinds en el contrato
    payload    jsonb,
    created_at timestamptz not null default now(),
    read_at    timestamptz
);
create index notifications_user_created_idx on notifications (user_id, created_at desc);

create table usage_events (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users (id) on delete cascade,
    kind       text not null check (kind in ('SIMULATION', 'RANDOM_MATCH', 'FULL_REPORT')),
    ref_id     text not null,
    refunded   boolean not null default false,
    created_at timestamptz not null default now(),
    unique (kind, ref_id)
);
create index usage_events_user_kind_created_idx on usage_events (user_id, kind, created_at);

-- =====================================================================
-- Seguridad
-- =====================================================================

create table blocks (
    blocker_id uuid not null references users (id) on delete cascade,
    blocked_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);
create index blocks_blocked_idx on blocks (blocked_id);

create table reports (
    id            uuid primary key default gen_random_uuid(),
    reporter_id   uuid references users (id), -- null si source = AUTO
    target_id     uuid references users (id), -- null si kind = AI_CONTENT
    chat_id       uuid references chats (id) on delete set null,
    message_id    uuid,
    kind          text not null default 'USER' check (kind in ('USER', 'AI_CONTENT')),
    simulation_id uuid references simulations (id),
    turn_index    smallint,
    reason        text not null check (reason in ('HARASSMENT', 'UNWANTED_SEXUAL', 'UNDERAGE', 'FAKE_OR_SCAM',
                      'SELF_HARM', 'AI_HARMFUL', 'OTHER')),
    details       text,
    snapshot      jsonb,
    source        text not null default 'USER' check (source in ('USER', 'AUTO')),
    priority      smallint not null default 0,
    status        text not null default 'OPEN' check (status in ('OPEN', 'RESOLVED')),
    resolution    text,
    resolved_by   uuid references users (id),
    created_at    timestamptz not null default now(),
    resolved_at   timestamptz
);
create index reports_status_priority_idx on reports (status, priority desc, created_at);

-- =====================================================================
-- Billing (ADR-0013)
-- =====================================================================

create table entitlements (
    user_id        uuid primary key references users (id),
    product_id     text not null,
    base_plan      text,
    purchase_token text not null unique,
    state          text not null check (state in ('ACTIVE', 'IN_GRACE_PERIOD', 'ON_HOLD', 'PAUSED',
                       'CANCELED', 'EXPIRED', 'REVOKED')),
    expires_at     timestamptz,
    auto_renewing  boolean not null default false,
    updated_at     timestamptz not null default now()
);

create table purchase_events (
    message_id     text primary key, -- id del mensaje Pub/Sub (RTDN): idempotencia
    purchase_token text,
    payload        jsonb not null,
    received_at    timestamptz not null default now()
);
create index purchase_events_token_idx on purchase_events (purchase_token);

-- =====================================================================
-- IA (ADR-0010)
-- =====================================================================

create table ai_usage (
    id          bigserial primary key,
    day         date not null, -- día en America/Bogota
    purpose     text not null,
    provider    text not null,
    model       text not null,
    user_id     uuid references users (id) on delete set null,
    tokens_in   int not null default 0,
    tokens_out  int not null default 0,
    cost_micros bigint not null default 0,
    created_at  timestamptz not null default now()
);
create index ai_usage_day_idx on ai_usage (day);

create table ai_usage_daily (
    day         date not null,
    provider    text not null,
    model       text not null,
    tokens_in   bigint not null default 0,
    tokens_out  bigint not null default 0,
    cost_micros bigint not null default 0,
    primary key (day, provider, model)
);
