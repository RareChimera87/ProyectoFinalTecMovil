# ADR-0004: Firestore como canal de tiempo real de solo lectura

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.8, E4.2, E4.3, E5.1, E5.2

## Contexto
Se pidió evaluar Firestore frente a WebSockets para el chat. Además, la fila de match y la simulación necesitan mostrar progreso en vivo, y la demo de la sustentación se ve mejor en tiempo real.

## Decisión
- Los clientes **solo leen** Firestore: `chats/{id}`, `chats/{id}/messages`, `simulations/{id}` y `matchTickets/{uid}`.
- Todas las escrituras pasan por la API, que valida membresía, bloqueos, consentimiento y estado, guarda en Postgres y luego escribe en Firestore con el Admin SDK.
- Reglas: todo denegado salvo lecturas de miembros o dueños; tests con el emulador.
- No hay indicador de 'escribiendo' en la 1.0 (sería una escritura de cliente).

## Alternativas consideradas
- **WebSockets en Cloud Run:** reconexión, offline y difusión entre instancias a mano; instancias calientes.
- **Clientes escribiendo en Firestore + Eventarc:** obliga a duplicar bloqueos y consentimiento en reglas; abre huecos de privacidad.
- **Polling cada 2 s:** más peticiones y peor demo.

## Consecuencias
- Offline y reconexión gratis en Android; reglas de 15 líneas.
- Postgres sigue siendo la verdad; Firestore es un espejo que se puede reconstruir.
- Una escritura más por mensaje (Postgres + Firestore), aceptable a esta escala.
