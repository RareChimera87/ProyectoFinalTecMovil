-- E4.2 · La fila conserva el último ticket de cada persona con su estado (arquitectura.md §7.4).
-- Antes un ticket solo existía mientras esperaba; ahora `GET /v1/match/queue` puede responder
-- MATCHED (con el id del match) o EXPIRED, igual que el documento `matchTickets/{uid}` de Firestore.
-- Cada persona sigue teniendo una sola fila: volver a entrar a la fila reemplaza el ticket anterior.

alter table match_queue
    add column status   text not null default 'WAITING' check (status in ('WAITING', 'MATCHED', 'EXPIRED')),
    add column match_id uuid references matches (id) on delete set null;

-- El emparejamiento solo mira tickets en espera.
drop index match_queue_mode_priority_idx;
create index match_queue_waiting_idx on match_queue (mode, priority desc, enqueued_at) where status = 'WAITING';
create index match_queue_expires_idx on match_queue (expires_at) where status = 'WAITING';

-- Barrido de matches sin decidir.
create index matches_pending_decide_by_idx on matches (decide_by) where status = 'PENDING_CHOICE';
