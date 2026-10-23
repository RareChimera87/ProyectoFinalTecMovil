-- E4.2 · Versión monótona por ticket para ordenar las publicaciones al espejo matchTickets/{uid}.
-- Un trigger asigna `nextval` en cada insert o update. Como el update de una misma fila se serializa
-- con el candado de fila, el orden de las versiones de un ticket coincide con el orden de confirmación.
-- El espejo (Firestore) solo escribe si la versión es mayor o igual a la que ya guarda, así una
-- escritura WAITING atrasada no pisa a MATCHED.

create sequence match_queue_version_seq;

alter table match_queue add column version bigint not null default 0;

create function match_queue_set_version() returns trigger
    language plpgsql as
$$
begin
    new.version := nextval('match_queue_version_seq');
    return new;
end;
$$;

create trigger match_queue_version_trg
    before insert or update on match_queue
    for each row execute function match_queue_set_version();
