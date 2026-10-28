# ParcheU · Guion de sustentación

Guion de 10 minutos para la sustentación del proyecto final (E10.7). Está pensado para que cada integrante
sepa exactamente qué dice, qué muestra y qué comando corre. Todo lo que se enseña funciona en el flavor `dev`
contra el backend del homeserver ([ADR-0033](adr/0033-backend-en-homeserver.md)); nada del guion depende de
Play Console. Qué se entrega de cara a Play y en qué estado está se declara en la sección 8, sin adornos.

Índice: [1 Reparto](#1-reparto-y-tiempos) · [2 Antes de empezar](#2-antes-de-empezar-lista-de-chequeo) ·
[3 Guion minuto a minuto](#3-guion-minuto-a-minuto) · [4 Demo de mutación](#4-demo-de-mutación-de-privacidad) ·
[5 Capturas frente a mockups](#5-capturas-roborazzi-frente-a-mockups) · [6 Recorrido por los ADR](#6-recorrido-por-los-adr) ·
[7 Costo de la IA](#7-costo-de-la-ia) · [8 Estado honesto](#8-estado-honesto-y-preguntas-esperadas) ·
[9 Plan B](#9-plan-b-si-algo-falla-en-vivo)

## 1. Reparto y tiempos

Los roles son los de [equipo.md](equipo.md): I1 backend, IA e infraestructura; I2 interfaz y experiencia; I3
social, seguridad de usuarios y publicación.

| Min | Parte | Responsable | Qué muestra |
|---|---|---|---|
| 0:00-1:00 | 1. Problema y propuesta | I2 | Una frase de valor, mockups de A2 y C1, el público (18-25, Bucaramanga) |
| 1:00-2:30 | 2. Arquitectura | I1 | [c4.md](c4.md) §2 y §3: contexto, contenedores y por qué el dev corre en el homeserver |
| 2:30-5:00 | 3. Demo en vivo del flujo central | I2 conduce, I3 hace de la segunda persona | Gemelo, grafo D1, simulación F4, resultado F7/F8 y decisión mutua |
| 5:00-6:30 | 4. Privacidad: demo de mutación | I1 | `scripts/demo-mutacion-privacidad.sh` rompe una regla y la suite lo detecta |
| 6:30-7:30 | 5. Seguridad de usuarios y publicación | I3 | Reportar y bloquear, borrado y exportación, cómo se publicaría (E10.x) |
| 7:30-8:30 | 6. Calidad y mockups | I2 | Capturas Roborazzi al lado de los mockups; ADR-0022 |
| 8:30-9:30 | 7. Costo de IA y ADR | I1 | Tabla de costo, control escalonado, 3 ADR clave |
| 9:30-10:00 | 8. Cierre | I3 | Qué aprendimos y qué queda; abre preguntas |

Quien no habla maneja el dispositivo o el terminal. Ensayar con cronómetro; si la demo en vivo se pasa de
2:30 min, I2 corta en la decisión mutua y deja el modo escenario para las preguntas.

## 2. Antes de empezar (lista de chequeo)

Hacerlo 30 minutos antes. Cada punto tiene su responsable.

- [ ] **I1:** API arriba: `curl https://<host del homeserver>/v1/health` responde 200 y `docker compose logs parcheu-api` no
      muestra errores ([operacion.md](operacion.md#backend-de-dev-en-el-homeserver)).
- [ ] **I1:** `PARCHEU_ENV=dev` (no `local`: en `local` la app real no inicia sesión) y el presupuesto diario de IA
      tiene margen (sección 7).
- [ ] **I1:** datos de demostración sembrados: `docker compose run --rm parcheu-migrate seed-demo`
      ([ADR-0027](adr/0027-usuarios-demo.md)); las dos cuentas de la demo ya existen y completaron el onboarding.
- [ ] **I2:** el APK `dev` instalado en dos teléfonos (o un teléfono y un emulador), con sesión iniciada y los
      términos aceptados, y la cuenta de I3 con ParcheU+ de prueba (`grant-plus`) si se va a enseñar F8.
- [ ] **I2:** `scripts/lado-a-lado.sh` ya corrido, con las imágenes abiertas en un visor.
- [ ] **I3:** terminal con el repositorio limpio (`git status` sin cambios) para la demo de mutación, que
      restaura el archivo pero exige árbol limpio en él.
- [ ] **Todos:** modo avión desactivado, pantalla sin suspensión, notificaciones silenciadas salvo las de ParcheU.
- [ ] **Todos:** [c4.md](c4.md) y [adr/README.md](adr/README.md) abiertos en el navegador.

## 3. Guion minuto a minuto

### 0:00-1:00 · Problema y propuesta (I2)

> "Conocer gente nueva es incómodo y la afinidad no se ve. ParcheU crea un gemelo digital de cada persona; dos
> gemelos conversan primero y, si les va bien, las personas deciden si se conectan."

Mostrar A2 (onboarding) y C1 (inicio). Decir una sola vez: Android con Kotlin y Compose, backend Ktor en
GCP, IA en Vertex. No entrar en detalles todavía.

### 1:00-2:30 · Arquitectura (I1)

Abrir [c4.md](c4.md). Tres ideas, en este orden:

1. **Una sola fuente de verdad:** Postgres; Firestore solo espeja para el tiempo real
   ([ADR-0003](adr/0003-postgres-fuente-de-verdad.md), [ADR-0004](adr/0004-firestore-canal-solo-lectura.md)).
2. **Todo lo lento va a una cola** (la simulación son unas 25 llamadas al modelo)
   ([ADR-0019](adr/0019-cloud-tasks-trabajo-asincrono.md)).
3. **El destino es Cloud Run y Cloud SQL, pero para el desarrollo corre en un homeserver** con la cola y el
   programador dentro del proceso ([ADR-0033](adr/0033-backend-en-homeserver.md)): mismo código, menos costo fijo.

### 2:30-5:00 · Demo del flujo central (I2 conduce, I3 es la otra persona)

| Paso | Pantalla | Qué decir |
|---|---|---|
| 1 | B1-B4 resumidos (ya hecho) y C1 | "El gemelo se forma con preguntas y se afina con memorias por modo" ([ADR-0032](adr/0032-memorias-por-modo.md)) |
| 2 | D1 Grafo | "Mi red como grafo; un amigo de amigos solo es visible con ParcheU+" ([ADR-0023](adr/0023-afinidad-estimada-amigos-de-amigos.md)) |
| 3 | F1 buscar match o "Simular" desde un perfil | I3 aparece en la fila; se crea la simulación |
| 4 | F4 Simulación en curso | Progreso en vivo (snapshot de Firestore); explicar que no gastó nada de más: hay cuota y tope |
| 5 | F7/F8 Resultado | Puntaje y informe (completo con ParcheU+) |
| 6 | Decisión mutua | I2 y I3 eligen "Conectar" a la vez; se abre el chat (E5) |

Plan de la demo: ensayar con las cuentas reales de la prueba, no con demos, porque los usuarios demo llevan
insignia visible y no escriben ([ADR-0027](adr/0027-usuarios-demo.md)).

### 5:00-6:30 · Privacidad (I1)

Ver la sección 4. Cierre de la parte: "no confiamos en que nadie olvide un filtro; lo comprobamos con propiedades
y lo demostramos rompiéndolo".

### 6:30-7:30 · Seguridad de usuarios y publicación (I3)

- Reportar y bloquear desde cualquier parte (E8.1, E8.7) y la moderación con IA que, si falla, deja el mensaje
  visible y lo registra ([ADR-0026](adr/0026-moderacion-ia-fail-open.md)).
- Borrado y exportación de cuenta (E8.3) con retención declarada en [arquitectura.md §10.2](arquitectura.md#102-retención-de-datos-fuente-para-privacidad-data-safety-e83-y-e88)
  ([ADR-0025](adr/0025-borrado-purga-y-retencion.md)); Ley 1581 ([ADR-0017](adr/0017-consentimiento-ley-1581.md)).
- Publicación: pipeline de release y estado de la prueba cerrada, tal como los deja la sección 8.

### 7:30-8:30 · Calidad y mockups (I2)

Ver la sección 5. Frase de cierre: "Roborazzi nos protege de regresiones; la fidelidad al diseño la juzga una
persona con checklist" ([ADR-0022](adr/0022-estrategia-de-pruebas-android.md)).

### 8:30-9:30 · Costo de IA y ADR (I1)

Ver las secciones 6 y 7. Un solo número para recordar: **una simulación completa cuesta del orden de 1 a 3
centavos de dólar** y el servidor corta en el tope diario.

### 9:30-10:00 · Cierre (I3)

Tres líneas: qué funciona hoy, qué aprendimos (sección 6, "Lo que aprendimos") y qué queda (sección 8). Abrir a
preguntas.

## 4. Demo de mutación de privacidad

La regla más delicada es que **Pareja nunca se vea para terceros** ([ADR-0012](adr/0012-privacidad-pareja.md)) y
que las memorias de un modo no se mezclen con otro ([ADR-0032](adr/0032-memorias-por-modo.md)). La suite de
invariantes (`backend/src/test/.../privacy`) los comprueba con propiedades sobre grafos aleatorios. La demo
muestra que **la suite detecta el defecto** si alguien lo introduce.

Qué hace el script `scripts/demo-mutacion-privacidad.sh` (sin argumentos hace `pareja`):

1. Reemplaza en `GraphVisibilityPolicy.kt` la condición que oculta las aristas de Pareja ajenas por `true`.
2. Corre la suite de privacidad y la deja fallar.
3. Restaura el archivo con `git checkout`, pase lo que pase. El árbol queda limpio.

Guion de la demo (1:30 min), con el terminal ya en la raíz del repositorio:

```bash
# 1. Control: sin mutación la suite pasa (código de salida 0)
scripts/demo-mutacion-privacidad.sh ninguna; echo "salida: $?"

# 2. Mutación 1: Pareja visible para terceros. La suite debe fallar (salida distinta de 0)
scripts/demo-mutacion-privacidad.sh pareja; echo "salida: $?"

# 3. Mutación 2: las memorias de cualquier modo alimentan al gemelo
scripts/demo-mutacion-privacidad.sh memorias; echo "salida: $?"

# 4. El árbol quedó limpio
git status --short
```

Qué decir mientras corre: "esta es la regla que protege a las parejas; acabo de borrarla del código y la suite
falla con un contraejemplo mínimo; el script la devuelve sola". Para repetir exactamente una corrida:
`PRIVACY_SEED=<n>`. Si el equipo tiene el portátil sin Gradle caliente, ejecutar el control (1) antes de empezar:
la primera corrida compila. Verificación sin Gradle: `scripts/demo-mutacion-privacidad.sh --verificar-anclas`.

## 5. Capturas Roborazzi frente a mockups

Las capturas viven en `android/app/src/test/screenshots/` (claro y oscuro de cada pantalla, versionadas) y
`./gradlew ... -Proborazzi.test.verify=true` falla si una pantalla cambia sin querer. La comparación con los
mockups es una **revisión humana con checklist**, sin umbral de píxeles ([ADR-0022](adr/0022-estrategia-de-pruebas-android.md)).

Armar las imágenes lado a lado antes de la demo:

```bash
scripts/lado-a-lado.sh                     # deja docs/mockups/png/lado-a-lado/<ID>_lado-a-lado.png
```

Requiere los PNG de los mockups de [docs/mockups/png/README.md](mockups/png/README.md) (exportados de Claude Design) e
ImageMagick. Las cuatro pantallas de E10.4:

| ID | Pantalla | Mockup | Captura Roborazzi (claro) |
|---|---|---|---|
| I1 | C1 Inicio | 04·C | `home/c1_3_de_5.png` |
| I2 | D1 Grafo | 08a·C | `parche/d1_grafo_light.png` |
| I3 | F8 Resultados ParcheU+ | 16b·C | `results/i3_light.png` |
| I4 | G1 Paywall | 18·C | `plus/g1_paywall_anual_light.png` |

Checklist que se dice en voz alta ante cada par: tokens exactos (hex de [mockups-inventario.md](mockups-inventario.md)),
espaciados con tolerancia de 4 dp, tipografía y jerarquía, y contraste AA en claro
([ADR-0030](adr/0030-contraste-tema-claro.md)). Si aparece una diferencia, se nombra y se explica si es aceptada;
no se esconde. Las diferencias aceptadas de E10.4 están en el PR de esa feature.

## 6. Recorrido por los ADR

Índice completo en [adr/README.md](adr/README.md). Para 90 segundos, estos siete cuentan la historia; el resto
queda como respaldo para preguntas.

| ADR | En una frase | Por qué importa |
|---|---|---|
| [0003](adr/0003-postgres-fuente-de-verdad.md) | Postgres es la única fuente de verdad | Sin pgvector ni CTE recursivas: el grafo se calcula con SQL simple y es probable de forma determinista |
| [0007](adr/0007-grafo-canvas-layout-radial.md) | Grafo en Canvas con layout radial determinista | Misma entrada, mismo dibujo; sirve para capturas estables |
| [0009](adr/0009-simulacion-agentes-y-juez.md) | Dos agentes independientes y un juez con rúbrica | El resultado no depende de un solo prompt |
| [0010](adr/0010-control-de-costo-ia.md) | Cuotas, caché, registro y tope diario escalonado | El gasto tiene techo conocido |
| [0012](adr/0012-privacidad-pareja.md) | Una policy, filtros en SQL y pruebas por propiedades | La demo de mutación de la sección 4 |
| [0019](adr/0019-cloud-tasks-trabajo-asincrono.md) | Cola para el trabajo asíncrono | Reintentos, deduplicación y tasa máxima |
| [0033](adr/0033-backend-en-homeserver.md) | Backend de dev en el homeserver | La decisión más reciente y la que más cambió la operación |

**Lo que aprendimos** (de los resultados de los spikes y de las revisiones, [revisiones.md](revisiones.md)):

- **Versiones de Kotlin:** el compilador 2.2 solo lee metadatos hasta 2.3 y varias librerías recientes ya vienen
  con 2.4; fijamos versiones anteriores ([ADR-0005](adr/0005-android-koin-datastore-sin-ksp.md), resultado del spike).
- **Modelos de IA:** los IDs cambian y no todos están en todas las regiones; por eso viven en configuración y no
  en el código, y los precios en una tabla con fecha ([ADR-0008](adr/0008-ia-vertex-proveedores-intercambiables.md), resultado del spike).
- **Costo fijo:** Cloud SQL y Cloud Run para pruebas eran un costo y un trámite que no aportaban a la demo; se
  pasó a un homeserver con el mismo código ([ADR-0033](adr/0033-backend-en-homeserver.md)).
- **Revisiones:** casi todos los hallazgos importantes fueron de privacidad en los bordes (un camino alterno que
  no pasaba por la policy). Por eso hay una sola fuente de verdad por regla, p. ej. `FofService` para amigos de
  amigos ([revisiones.md](revisiones.md)).

## 7. Costo de la IA

Datos de [ADR-0008](adr/0008-ia-vertex-proveedores-intercambiables.md) (precios consultados el 22 oct 2026) y
`backend/src/main/resources/ai/modelos.properties`. Son estimaciones; el gasto real se mide en `ai_usage`.

| Concepto | Valor |
|---|---|
| Modelo de chat y juez (predeterminado) | `gemini-3.8-flash`: 0,825 USD por millón de tokens de entrada y 4,125 de salida hasta el 31 dic 2026; 1,65 y 8,25 desde el 1 ene 2027 |
| Modelo ligero | `gemini-3.5-flash-lite`: 0,33 y 2,75 |
| Una llamada del juez (352 tokens de entrada, 241 de salida) | ≈ 0,0013 USD |
| Una simulación (unas 25 llamadas cortas) | del orden de 1 a 3 centavos de USD |
| Cuota gratis | 5 simulaciones por semana, 1 match al día, 1 informe al mes ([ADR-0010](adr/0010-control-de-costo-ia.md)) |
| Peor caso gratis por persona y semana | 5 × 3 centavos = 0,15 USD (cota alta de la estimación) |
| Tope diario por defecto | `AI_DAILY_BUDGET_USD=3` (con 3 centavos por simulación, ≈ 100 simulaciones al día) |

Control escalonado (`DailyBudgetGuard`): por debajo del 70 % del tope todo normal; entre el 70 y el 100 %, todo
pasa al modelo ligero y se anota en `simulations.model`; al 100 % la API responde 503 `AI_BUDGET` con `retryAt` y
no cobra cupo. Además, la tasa máxima de las colas es el techo físico (en dev, la concurrencia de la cola en
proceso) y el presupuesto de facturación de GCP avisa al 50 % y al 90 %. Qué hacer si salta:
[operacion.md](operacion.md#ai-budget-exceeded).

Para la pregunta "¿cuánto cuesta con 1000 usuarios activos?": con los supuestos de arriba y todos agotando la
cuota gratis cada semana, el gasto de IA estaría por debajo de 150 USD por semana; con el tope diario de 3 USD
nunca pasa de 21 USD por semana. Decirlo como cota, no como pronóstico.

## 8. Estado honesto y preguntas esperadas

Decir esto antes de que lo pregunten. Actualizar esta tabla el día antes con el estado real.

| Tema | Estado |
|---|---|
| App y backend en dev | Funcionales de punta a punta en el flavor `dev` contra el homeserver |
| Publicación en Play | Prioridad del usuario del 27 oct: la app funcional en `dev` va primero. Pipeline de release, ficha y Data safety están como código y documentos; la ejecución en Play Console (prueba cerrada, acceso a producción) depende de pasos humanos en [pendientes-humanos.md](pendientes-humanos.md) y [play-console.md](play-console.md). **Registrar aquí la fecha real antes de sustentar.** |
| Prod en GCP | Terraform escrito y validado; `apply` de prod solo lo ejecuta el usuario y no se ha hecho por ahora |
| Compras | Verificación en servidor lista; en dev se prueba con `grant-plus` y sin RTDN |
| Notificaciones push | Reales con `PARCHEU_ENV=dev`; no hay RTDN de renovaciones en el homeserver |
| ADR-0029 (texto del ahorro del plan anual) | Propuesta, pendiente de confirmación del usuario |

Preguntas probables y respuesta corta:

- **¿Por qué no Hilt/Room?** Koin y DataStore alcanzan y evitan KSP/kapt con Kotlin 2.2 ([ADR-0005](adr/0005-android-koin-datastore-sin-ksp.md)).
- **¿Y si la IA se cae o se acaba el presupuesto?** Error explícito con `retryAt` y cupo devuelto; moderación fail-open con aviso a una persona.
- **¿Qué pasa si el homeserver se reinicia a mitad de una simulación?** El barrido `simulation-recovery` la reencola
  (cada 15 min, ventana de 24 h) ([deploy/homelab/README.md](../deploy/homelab/README.md)).
- **¿Cómo sé que los usuarios demo no engañan?** Insignia visible, nunca en Pareja, no escriben ([ADR-0027](adr/0027-usuarios-demo.md)).

## 9. Plan B si algo falla en vivo

| Falla | Qué hacer |
|---|---|
| El backend no responde | I1 revisa `docker compose logs parcheu-api` ([operacion.md](operacion.md#backend-de-dev-en-el-homeserver)); mientras tanto I2 enseña las capturas de la sección 5 y [c4.md](c4.md) |
| La simulación tarda o falla | Enseñar una simulación ya terminada de la cuenta de ensayo (F7/F8); explicar `AiBudgetExceeded` si fue el tope |
| La fila está vacía | Es el riesgo 8 del [PLAN](PLAN.md): usar "Simular" desde un perfil o los usuarios demo sembrados |
| La demo de mutación no compila | Mostrar la salida grabada del ensayo (guardarla el día anterior) y `--verificar-anclas` |
| Sin red en la sala | Hotspot del teléfono de I3; si el homeserver no es alcanzable, todo el guion se puede contar con capturas y [c4.md](c4.md) |
