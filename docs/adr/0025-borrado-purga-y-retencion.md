# ADR-0025: Borrado lógico, purga asíncrona y política de retención

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E8.3, E8.8, E8.4, E10.8

## Contexto
Play exige borrado de cuenta en la app y en la web; la Ley 1581 da derecho de supresión; Data safety y `/privacidad` deben declarar cuánto tiempo se guarda cada dato. Hay datos personales dentro de jsonb (instantáneas de reportes, transcripciones, payloads) y en Firestore.

## Decisión
- `DELETE /v1/me` hace un **borrado lógico inmediato** (`deleted_at`: invisible en todo) y encola `purge-user` (cola `maintenance`).
- La purga sigue la **tabla de retención de arquitectura.md §10.2**: borra filas personales; anonimiza los mensajes propios en chats compartidos; deja al otro participante solo el puntaje de las simulaciones compartidas; limpia las columnas jsonb del registro `PersonalJsonPaths`; borra documentos de Firestore, tokens FCM y el usuario de Firebase Auth.
- Se conservan, sin datos de contacto, los consentimientos (prueba de la autorización) y los eventos de compra (obligación contable); plazos marcados '(verificar)'.
- Un **job diario `retention`** (E8.8) aplica los plazos a los usuarios activos.
- Un test de arquitectura falla si aparece una columna jsonb sin clasificar.

## Alternativas consideradas
- **Borrado físico síncrono:** lento, toca varios sistemas y falla a medias.
- **Retener todo indefinidamente:** incumple la Ley 1581 y complica Data safety.

## Consecuencias
- Una sola tabla de retención alimenta la política, la ficha y los tests.
- El historial del otro usuario pierde detalle cuando alguien borra su cuenta (decisión a favor de la privacidad).
