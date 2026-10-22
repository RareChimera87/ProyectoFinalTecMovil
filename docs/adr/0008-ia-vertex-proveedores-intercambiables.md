# ADR-0008: IA en Vertex AI con proveedores intercambiables

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.9

## Contexto
El gemelo debe funcionar con Gemini (créditos de GCP en Vertex AI) y opcionalmente con Claude, intercambiables por configuración. Los IDs de modelo cambian con el tiempo y algunos se retiran. Los créditos promocionales de GCP **no siempre cubren modelos de socios** (como Claude en Model Garden).

## Decisión
- Interfaz `LlmClient` con `GeminiVertexClient` (SDK Google Gen AI en modo Vertex), `ClaudeVertexClient` (SDK de Anthropic con backend Vertex) y `FakeLlmClient`.
- Configuración: `AI_PROVIDER`, `AI_MODEL_CHAT`, `AI_MODEL_JUDGE`, `AI_MODEL_LITE`. **Gemini Flash es el predeterminado.** Los IDs se fijan en configuración el día del spike con los GA vigentes en Model Garden; nunca en el código.
- Claude solo se activa después de confirmar en la consola de facturación que los créditos lo cubren y que está disponible en una región compatible.

## Alternativas consideradas
- **API de Anthropic directa con API key:** fuera de los créditos y con un secreto más.
- **Fijar IDs en el plan:** se quedan viejos; un modelo retirado rompería la app en producción.

## Consecuencias
- Cambiar de proveedor o de modelo es una variable de entorno y un despliegue.
- La comparación de calidad entre proveedores se hace con el Fake y pruebas manuales; no hay suite de evaluación automática en la 1.0.

## Resultado del spike

Hecho el **22 oct 2026** (E0.9) con llamadas reales a Vertex AI en `parcheu-dev` (API `aiplatform.googleapis.com` habilitada ese día) y la página de precios de Vertex consultada el mismo día. Los valores quedan en `backend/src/main/resources/ai/modelos.properties`; las variables `AI_*` los sobrescriben por entorno.

**Modelos fijados (Gemini, predeterminado):**

| Nivel | ID | Estado | Dónde responde |
|---|---|---|---|
| `AI_MODEL_CHAT` | `gemini-3.8-flash` | GA (`launchStage` = GA) | multirregión `us` y `global`; **no** en `us-central1` (404) |
| `AI_MODEL_JUDGE` | `gemini-3.8-flash` | GA | igual |
| `AI_MODEL_LITE` | `gemini-3.5-flash-lite` | GA | igual |

- **Ubicación:** `AI_VERTEX_LOCATION=us` (endpoint `aiplatform.us.rep.googleapis.com`). Los Gemini 3.x no están publicados en `us-central1`; la multirregión `us` mantiene el procesamiento en EE. UU. y por tanto no cambia lo decidido en ADR-0020 (transferencia a EE. UU.). `global` se descarta porque puede procesar fuera de EE. UU.
- **Razonamiento:** Gemini 3.x cobra el razonamiento como salida y lo cuenta dentro de `maxOutputTokens`. Se fija `thinkingLevel=LOW` (lo aceptan ambos modelos; `MINIMAL` da 400 en 3.8 Flash). Sin nivel, una respuesta corta del juez gastó 342 tokens de razonamiento frente a 0 con `LOW`.
- **Salida JSON:** `responseMimeType=application/json` + `responseJsonSchema` devuelve JSON válido en ambos modelos; el backend lo valida igual (`JsonSchemaCheck`).
- **Alternativas probadas:** `gemini-3.5-flash` (GA, más caro: 1,65/9,90), `gemini-3.6-flash` y `gemini-3.7-flash` (GA), `gemini-3.1-flash-lite` (GA, más barato: 0,275/1,65; queda con precio en la tabla por si se quiere bajar el costo del nivel lite), `gemini-2.5-flash` y `-lite` (sí están en `us-central1`, generación anterior).

**Precios usados en la tabla de costos** (USD por millón de tokens, tarifa estándar on-demand "non-global", contexto ≤ 200K; la salida incluye razonamiento):

| Modelo | Entrada | Salida | Vigencia |
|---|---|---|---|
| `gemini-3.8-flash` | 0,825 | 4,125 | precio introductorio hasta el 31 dic 2026 |
| `gemini-3.8-flash` | 1,65 | 8,25 | desde el 1 ene 2027 (la tabla cambia sola por fecha) |
| `gemini-3.5-flash-lite` | 0,33 | 2,75 | |
| `claude-sonnet-5-5` (us) | 2,20 | 11,00 | |
| `claude-haiku-5-5` (us) | 0,11 | 0,55 | |

Con estos precios una llamada real del juez (352 tokens de entrada, 241 de salida) cuesta ≈ 0,0013 USD, y una simulación de unas 25 llamadas cortas queda en el orden de 1-3 centavos de USD, como estimaba arquitectura.md §7.2.

**Claude en Vertex:**

- SDK: `com.anthropic:anthropic-java-vertex` 3.1.0 + `anthropic-java-client-okhttp` 3.1.0 (`VertexBackend`, ADC). La multirregión `us` está soportada por el SDK.
- `claude-sonnet-5-5` y `claude-haiku-5-5` **existen** en Vertex para `us` y `global` (en `us-east5` dan 404), pero el proyecto tiene **cuota 0**: 429 `Quota exceeded for aiplatform.googleapis.com/us_multi_region_online_prediction_requests_per_base_model` (base models `anthropic-claude-sonnet` y `anthropic-claude-haiku`).
- **¿Los créditos promocionales cubren a Claude?** **Sin confirmar.** No hay API que muestre el alcance de los créditos de la cuenta de facturación `01D4D0-42F08D-DF5FE6`; hay que verlo en la consola (Facturación → Créditos → alcance del crédito) antes de activar Claude. Por regla general los créditos de prueba de GCP no cubren modelos de socios de Model Garden; hasta verlo, **Claude queda desactivado** y Gemini es el proveedor (`AI_PROVIDER=gemini`). Pasos en `docs/pendientes-humanos.md` (E0.9).
