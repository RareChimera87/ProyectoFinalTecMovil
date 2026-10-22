# ADR-0017: Consentimiento por modo en una tabla que solo agrega filas (Ley 1581)

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E2.1, E2.5, E8.2, E8.3, E9.2

## Contexto
Los chats reales entrenan al gemelo solo con consentimiento explícito. En Colombia la Ley 1581 de 2012 exige consentimiento previo, expreso e informado, y derechos de consulta, actualización y supresión.

## Decisión
- Tabla `consents` que solo agrega filas (la cuenta de la API no tiene UPDATE ni DELETE), con tipo, valor y versión del texto.
- Consentimiento de aprendizaje **por modo**; Pareja apagado por defecto.
- `learnable` se fija al escribir el mensaje; la extracción exige además el consentimiento vigente. Revocar borra las memorias PENDING del modo.
- Exportación (`GET /v1/me/export`) y borrado completo; mención de la Ley 1581 en el consentimiento y en `/privacidad`.

## Alternativas consideradas
- Un booleano `learn_from_chats`: no deja auditoría ni distingue modos.

## Consecuencias
- Historial auditable para la sustentación y para la ficha de Play.
- Consultar el estado vigente requiere tomar la última fila por tipo (índice dedicado).

## Enmienda (22 oct 2026, ronda de corrección)
- **Términos y política:** A4 informa y pide la aceptación expresa; tras el login la app llama `POST /v1/me/terms`, que agrega filas TERMS y PRIVACY con `text_version = LEGAL_TEXT_VERSION`. Sin ellas la API responde 403 `TERMS_REQUIRED`; si la versión cambia, el usuario vuelve a aceptar.
- **Aviso de IA y transferencia:** A4 y `/privacidad` informan que las respuestas y mensajes se procesan con IA de Google Cloud en EE. UU. ([ADR-0020](0020-region-y-transferencia-internacional.md)).
- **Enlaces a la política desde la 0.1** (stub de A4 en E0.7) y en H3.
- **Retención y supresión:** [ADR-0025](0025-borrado-purga-y-retencion.md).
