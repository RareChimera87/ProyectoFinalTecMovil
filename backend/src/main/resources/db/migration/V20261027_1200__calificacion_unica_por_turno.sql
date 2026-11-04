-- E9.3: una calificación por turno simulado y usuario. Reintentar el POST no duplica filas ni sesga la métrica.
create unique index twin_feedback_rating_unique_idx
    on twin_feedback (user_id, simulation_id, turn_index)
    where kind = 'RATING';
