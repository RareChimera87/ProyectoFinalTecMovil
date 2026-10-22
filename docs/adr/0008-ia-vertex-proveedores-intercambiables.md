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
_Completar en E0.9, **a más tardar el 30 oct 2026**: IDs verificados de Flash, Flash-Lite (y Claude si aplica), región, si los créditos cubren a Claude, precios por millón de tokens usados en la tabla de costos._
