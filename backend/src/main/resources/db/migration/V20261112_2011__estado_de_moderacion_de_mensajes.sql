-- Barrido de moderación: sin esta marca no hay forma de saber qué mensajes quedaron sin moderar tras un
-- reinicio de la cola en proceso (TASKS_MODE=inline) o un encolado fallido.
-- `moderated_at` nulo = pendiente; la moderación lo fija al terminar (también si decide no moderar por tope o muestreo).
-- `moderation_help_pending` = el aviso de ayuda ante SELF_HARM falló y falta entregarlo (`moderate-help`).
alter table chat_messages add column moderated_at timestamptz;
alter table chat_messages add column moderation_help_pending boolean not null default false;
-- Reencolados del barrido por mensaje: tras SWEEP_MAX_REQUEUES se da por moderado (fail-open) para acotar el gasto de IA.
alter table chat_messages add column moderation_sweeps integer not null default 0;

-- Lo anterior a esta migración no se vuelve a moderar: se da por revisado para que el barrido no encole todo el historial.
update chat_messages set moderated_at = created_at;

create index chat_messages_unmoderated_idx on chat_messages (created_at) where moderated_at is null and sender_id is not null;
create index chat_messages_help_pending_idx on chat_messages (created_at) where moderation_help_pending;
