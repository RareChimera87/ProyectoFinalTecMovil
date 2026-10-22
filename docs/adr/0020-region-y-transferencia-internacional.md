# ADR-0020: Región us-central1 y transferencia internacional de datos (Ley 1581)

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.5, E2.2, E8.4, E10.8

## Contexto
Los usuarios están en Bucaramanga. No hay región de GCP en Colombia; Vertex AI (Gemini y Claude en Model Garden) tiene la mayor disponibilidad de modelos en `us-central1`. La Ley 1581 de 2012 regula la transferencia de datos personales a otros países.

## Decisión
- Todo en **us-central1** (Cloud Run, Cloud SQL, Vertex AI, Cloud Tasks) y Firestore en **nam5**.
- Se informa de forma expresa: A4 dice que las respuestas y mensajes se procesan con IA de Google Cloud en EE. UU., y `/privacidad` tiene la sección 'Transferencia internacional'.
- La transferencia se apoya en que la SIC incluye a EE. UU. entre los países con nivel adecuado de protección (Circular Externa 005 de 2017; **verificar vigencia**) y en la autorización expresa que se registra en `consents`.

## Alternativas consideradas
- **southamerica-east1 (São Paulo) o southamerica-west1 (Santiago):** menor latencia, pero menos modelos de Vertex disponibles y precios distintos; complica tener IA y base de datos en la misma región.
- **No informar la transferencia:** riesgo legal y de revisión de Play.

## Consecuencias
- Latencia extra de red de unos 80-120 ms por petición desde Bucaramanga (verificar con el uptime check); aceptable porque el tiempo real va por Firestore y la simulación es asíncrona.
- Si la SIC cambia su lista, basta con reforzar el texto de autorización; la arquitectura no cambia.
