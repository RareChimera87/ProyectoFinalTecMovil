# Parche Vivo: guía para rediseñar pantallas

Dirección visual A, elegida el 13 nov 2026. La marca no cambia (violeta `#6D4AFF`, naranja `#FF8A3D`, Amistad `#2EC4A6`, Estudio `#3A86FF`, Pareja `#FF5C7A`). Lo que cambia es la jerarquía: **cada pantalla tiene un protagonista**, que puede ser un bloque de color grande, un número gigante o la mascota. La mascota nueva es **"La P que mira"**.

Todo vive en la rama `rediseno/parche-vivo`: tokens, tema, mascota, logo, ícono, componentes y las pantallas de los cuatro grupos (G-A acceso y gemelo, G-B Inicio y Conocer, G-C Tu parche y social, G-D chats, perfil, ajustes y ParcheU+), ya integradas. Inicio (C1/I1, `feature/home/HomeScreen.kt`) fue la pantalla de referencia. Esta guía describe lo que existe hoy; la tabla del final dice dónde quedó cada pantalla y lo pendiente.

## Principios

1. **Un protagonista por pantalla.** Arriba del pliegue va un `HeroBlock`, un número en Sora 800 o la mascota en acción. El resto pesa menos.
2. **El color del modo organiza.** Todo lo que pertenece a una relación de un modo (simulación, resultado, sugerencia, chat) va en una `ModeSpineCard` con el lomo del modo. No dejes el modo solo en un chip de 10 sp.
3. **La mascota es la unidad de medida.** El cupo semanal se dibuja con `QuotaMascots` y no con una barra. En simulación las dos P se miran (una con `mirrored = true`).
4. **Tres pesos de tarjeta, sin sombras.** Héroe (radio 24, relleno de color), estado (radio 18, `ModeSpineCard`) y acceso (radio 18, `AccessTile` tonal o con borde de 1 dp). La profundidad sale del relleno, no de la elevación.
5. **Fotos de la familia.** Toda foto de stock va en duotono del modo (`DuotonePhoto` o `Modifier.duotone`) con esquinas de 20.
6. **El tono no cambia.** Textos honestos y sin contadores falsos; los textos, flujos y estados se quedan como están.

## Tokens

Todo vive en `android/designsystem/src/main/java/me/santiagoreina/parcheu/designsystem/theme/`. Las pantallas leen `ParcheUTheme.colors`, `.shapes`, `.spacing` y `.textStyles` o `MaterialTheme`, nunca hex sueltos.

### Color

| Token | Oscuro | Claro | Uso |
|---|---|---|---|
| `bg` | `#14111F` | `#F4F2F8` (lila pálido, antes hueso `#FAF7F2`) | fondo de pantalla |
| `s1` | `#1F1B2E` | `#FFFFFF` | tarjetas |
| `s2` | `#2B2641` | `#ECE9F3` (antes `#F1ECE4`) | pistas, campos, chips neutros |
| `ln` | `#3A3354` | `#E4DFF0` (antes `#E2DBCF`) | bordes de 1 dp |
| `hero` / `onHero` | `#6D4AFF` / blanco | igual | relleno y texto del `HeroBlock` (5,15:1) |
| `heroQuiet` | `#8F75FF` | igual | mascotas apagadas sobre el héroe (decorativo) |
| `friendship/couple/study/accent.container` | `#12352E` `#3C1520` `#142A4D` `#3A2A12` | `#D9F3EE` `#FFE2E7` `#E0ECFF` `#FFF0DC` | contenedor tonal del modo |
| `….onContainer` | el tint del modo | el texto AA del modo (Pareja `#A3203A`) | texto sobre el contenedor |

El resto (`tx`, `mu`, `prt`, `prc`, `onprc`, tints de modo y `error`) no cambia. `ContrastTest` recorre todos los pares declarados en `declaredTextPairs()` (incluidos los nuevos `onHero/hero`, `onContainer/*` y `tx/*Container`) y exige 4,5:1 en los dos temas. Si combinas texto y fondo de otra forma, agrega el par ahí.

### Tipografía

Sora ahora trae el 800 (`res/font/sora_extrabold.ttf`, el mismo archivo estático que sirve Google Fonts para Sora v17, OFL 1.1, licencia en `android/designsystem/licenses/OFL-Sora.txt`).

| Rol | Estilo |
|---|---|
| Número héroe | `ParcheUTheme.textStyles.hero56` (Sora 800 56, −3 %) o `hero46` |
| `displayLarge/Medium/Small` | Sora 800 44 / 34 / 31, −2 % |
| `headlineLarge/Medium` (titulares de pantalla y héroe) | Sora 800 28 / 26, −2 % |
| `headlineSmall` | Sora 700 22 |
| `title*`, `body*`, `label*` | sin cambios (Sora 600 / Inter) |

Escala de la dirección: 56 héroe · 28 título de pantalla · 19 app bar · 15 título de tarjeta · 14 cuerpo · 12 apoyo · 10 overline.

### Forma y espacio

- `ParcheURadius.Card` pasó de 20 a **18**; `Hero` 24; `Photo` 20 (`ParcheUTheme.shapes.photo`); `ButtonCompact` de 12 a **14**. Los chips de filtro e interés ahora son píldoras.
- `ParcheUSizes.ModeSpine` = 5 dp y `ParcheUSizes.QuotaMascot` = 24 dp.
- `ParcheUTheme.spacing.blockGapTight` = 10 dp entre bloques de la dirección A. El gutter de pantalla sigue en 16 (el mockup usa 14, pero no se cambió globalmente para no mover todas las pantallas).

## La mascota: "La P que mira"

Es el monograma P de ParcheU, y su contraforma es un ojo grande con brillo. Está en `mascot/TwinMascot.kt`. La API pública **no cambió**: `TwinMascot(modifier, size, tone, error, talking, animationsEnabled, contentDescription)`, con `TwinMascotSize` S 30 / M 56 / L 80 / XL 116, alto 1,125 × ancho.

- Color por `tone`: violeta para el usuario y el pleno del modo para el otro. Ojo y boca en tinta `#14111F`. El tallo lleva un brillo blanco al 24 %.
- `error = true`: gris (luma Rec. 709) con opacidad 0,5.
- `talking = true`: la boca pasa a óvalo y abre y cierra. El brillo del ojo mira de lado a lado y la P se balancea ±4° sobre el pie. Respeta "reducir animaciones" (`rememberAnimationsEnabled()`, escala de animación 0): queda quieta con la boca abierta.
- Sin `contentDescription` es decorativa. Si la mascota es lo único que dice algo, usa `TWIN_MASCOT_DESCRIPTION` ("Tu gemelo, la P de ParcheU") o una frase propia. **Nunca "robot".**

**Regla de la mascota pequeña.** Con ancho ≤ `MASCOT_SIMPLIFIED_MAX_WIDTH` (30 dp) se dibuja la variante simplificada, sin boca ni brillo del tallo y con el ojo más grande (13 × 16 en vez de 10 × 12,5). `TwinMascotSize.S` ya es simplificada. No hagas mascotas de menos de 24 dp.

Para dibujarla sin animación, a cualquier ancho y con colores libres, usa `TwinMascotGlyph`:

```kotlin
fun TwinMascotGlyph(
    width: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    eyeColor: Color = ParcheUPalette.Ink,   // sobre un bloque de color, pasa el color del bloque
    mouth: MascotMouth = MascotMouth.Quiet, // Quiet | Talking
    simplified: Boolean = width <= MASCOT_SIMPLIFIED_MAX_WIDTH,
    mirrored: Boolean = false,              // la otra persona, mirando hacia ti
    lookingLeft: Boolean = false            // experimental: sin espejar, brillo a la izquierda (sin uso)
)
```

`lookingLeft` es una alternativa en evaluación a `mirrored` (la P espejada se lee como una "q"): deja la P derecha y corre el brillo del ojo a la izquierda. Ninguna pantalla lo usa todavía.

### Las dos P frente a frente (`feature/simulation/SimulationParts.kt`)

- `SimulationMascot(color, width, error, mirrored, talking, animate, eyeColor)`: la P de una escena, con la boca y el balanceo al conversar (respeta "reducir animaciones") y gris al 50 % con `error`.
- `FacingTwins(otherColor, width, speaker = TwinSpeaker.Nobody, gap = 20.dp, error, animate, myColor = primary, otherEyeColor = Ink)`: tu P en violeta a la izquierda y la del otro espejada, del color de su modo, a la derecha. Solo anima la que habla.
- `TwinSpeaker { Me, Other, Nobody }`: quién habla en la escena.

Se usan en F2 (incluido "vencido"), F4-F5, F6, F7/F8/I3 y la conversación de gemelos. Son decorativas: la pantalla describe la escena.

## Logo e ícono

- `ParcheULogo(modifier, size: WordmarkSize = Medium, textColor = colors.wordmark, mascotColor = Violet)`: lockup con la P-mascota como P de "ParcheU", "arche" en Sora 700 y la U naranja. TalkBack lo lee como "ParcheU". Inicio ya lo usa en la barra.
- `Wordmark(darkTheme, …)` sigue existiendo (texto, ahora en Sora). Úsalo solo donde la mascota sobre.
- `ParcheUAppIcon(size, modifier, background = Violet, foreground = White)`: el ícono como composable. `OnboardingAppIcon` (A1, splash) delega en él y el centro del QR de C5 lo usa directo. El splash y el onboarding usan `ParcheULogo` (ya no hay `OnboardingMascot` ni `OnboardingWordmark`).
- Launcher: `res/drawable/ic_launcher_foreground.xml` (P blanca con el ojo y la boca como huecos), `ic_launcher_monochrome.xml` (íconos temáticos), `ic_launcher_background.xml` (violeta, sin cambios) y los `mipmap-*/ic_launcher{,_round}.webp` heredados, regenerados desde la misma geometría. El splash del sistema (`ic_splash`) los compone. No cambiaron ni el `applicationId` ni el nombre.

## Componentes

Todos tienen previews en `components/compound/ParcheVivoGallery.kt` y capturas en `android/designsystem/src/test/screenshots/parchevivo/`.

### HeroBlock (`components/compound/HeroBlock.kt`)

```kotlin
fun HeroBlock(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    titleSuffix: String? = null,                         // "libres de 5", pegado al número en Sora 600 15
    subtitle: String? = null,
    titleSize: HeroTitleSize = HeroTitleSize.Number,     // Number 56 | NumberCompact 46 | Headline 28
    colors: HeroBlockColors = HeroBlockDefaults.colors(), // o HeroBlockDefaults.modeColors(colors.study)
    badge: (@Composable () -> Unit)? = null,             // arriba a la derecha
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,            // botón al final
    content: (@Composable ColumnScope.() -> Unit)? = null // entre el título y el subtítulo
)
```

El título es un solo nodo con rol de encabezado: "2 libres de 5" se lee y se busca en pruebas como un solo texto. Si pones `ParcheUPlusBadge` en el `badge`, pásale `onHero = true` (píldora tinta) o no se lee en el tema claro.

```kotlin
HeroBlock(
    title = "2", titleSuffix = "libres de 5", titleSize = HeroTitleSize.NumberCompact,
    overline = "Simulaciones · esta semana", subtitle = "Te quedan 2 · se renuevan el lunes",
    badge = { ParcheUPlusBadge(onHero = true) }
) { QuotaMascots(used = 3, limit = 5) }
```

### QuotaMascots (`components/compound/QuotaMascots.kt`)

```kotlin
fun QuotaMascots(
    used: Int,
    limit: Int?,                                         // null = ParcheU+ sin límite (5 encendidas + ∞)
    modifier: Modifier = Modifier,
    colors: QuotaMascotsColors = QuotaMascotsDefaults.onHero(), // onSurface() fuera del héroe
    mascotWidth: Dp = ParcheUSizes.QuotaMascot,
    unlimitedSlots: Int = 5,
    contentDescription: String = quotaMascotsDescription(used, limit)
)
```

Brillan las que te quedan (`limit − used`). Para TalkBack es un solo nodo: por defecto dice "Te quedan 2 de 5 simulaciones esta semana" o "Simulaciones sin límite con ParcheU+". Inicio pasa su propia frase ("Usaste 3 de 5…").

### ModeSpineCard (`components/base/Cards.kt`)

```kotlin
fun ModeSpineCard(
    mode: ModeColors,                 // colors.friendship | couple | study
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ModeSpineCardDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit
)
```

Superficie s1 con radio 18 y lomo de 5 dp del `mode.solid`. Dentro, di el modo también en texto ("· Estudio" o `ModeChip(style = ModeChipStyle.Tonal)`), porque el lomo solo es decorativo. Para que una barra de avance vaya con el modo, usa `ParcheULinearProgress(…, color = mode.solid)`.

### AccessTile (`components/compound/AccessTile.kt`)

```kotlin
fun AccessTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Painter? = null,
    style: AccessTileStyle = AccessTileStyle.Tonal,  // Tonal (prc) | Outline (s1 + borde ln)
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
)
```

Sirve para atajos como Match al azar, Presentar o Invitar. Cuando dos tiles van juntos, usa estilos distintos para que no pesen igual (en el mockup, 1,25 : 1).

### Duotono (`components/compound/Duotone.kt`)

```kotlin
fun Modifier.duotone(mode: ModeColors, shadow: Color = ParcheUPalette.Ink): Modifier
fun Modifier.duotone(shadow: Color, highlight: Color): Modifier
fun duotoneColorFilter(shadow: Color, highlight: Color): ColorFilter   // para Image(colorFilter = …)
fun duotoneColorMatrix(shadow: Color, highlight: Color): ColorMatrix
fun DuotonePhoto(painter: Painter, mode: ModeColors, contentDescription: String?,
                 modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop)
```

Las sombras quedan en tinta y las luces en el pleno del modo. Funciona con los WebP de `drawable-nodpi` (captura `android/app/src/test/screenshots/parchevivo/duotone_webp.png`). `Modifier.duotone` va después del `clip`.

### PuGraphStyle (`designsystem/graph/PuGraph.kt`)

`PuGraph(…, style = PuGraphStyle.Classic | ParcheVivo)`. Con `ParcheVivo`, "Tú" es La P que mira blanca sobre violeta (si cabe a 24 dp o más; si no, "Tú" en texto), los nodos se tiñen con el pleno de su modo (opacidad según afinidad) y los anillos llevan rótulo ("CERCANOS", "POR CONOCER"). No cambia el layout ni la semántica. Lo usan D1 (Tu parche), D6 (amigos de amigos), D9 (evolución, miniaturas) y el ejemplo de A4. El valor por defecto sigue en `Classic`.

### Componentes de `:app` compartidos

- **`StateIllustration(prop, modifier, error = false)`** (`core/ui/StateIllustrations.kt`): la P (gris y apagada con `error`) junto a un objeto de contexto `StateProp` (`Chat`, `Map`, `Cup`, `Notebook`). `AppError.stateProp()` elige el objeto de un error: mapa sin conexión, taza con el cupo de IA y cuaderno en el resto. Pásala como `illustration` de `EmptyState`/`ErrorState`; ya la usan los vacíos y errores de B0, B1, B2-B3, C2, C3, C4, C5, D6, D8, D9, F2, chats, bloqueados, G4 y ajustes de privacidad. Los estados con ilustración propia (F1 con el tono del modo, F2 vencido con las dos P, C2 vacío con la campana, D3/D5) la conservan.
- **`DestructiveButton(text, onClick, modifier, enabled)`** (`core/ui/DestructiveButton.kt`): relleno `error` con `onError`, mismo alto y radio que `PrimaryButton`. Para lo que no se deshace (borrar la cuenta, bloquear).
- **`ParchePlusHero(title, body, onUnlock, overline = "Con ParcheU+", actionLabel = "Ver ParcheU+", illustration = { PlusPeopleIllustration() })`** (`feature/parche/ParcheVivoParts.kt`): `HeroBlock` de una función de ParcheU+ bloqueada en gratis (D7, D8, D9), con la insignia sobre el héroe. No muestra datos reales.

### Fotos de B1 (`feature/twinquestions/TwinQuestionPhotos.kt`)

B1 no usa `Modifier.duotone` tal cual: las fotos nocturnas quedaban casi enteras en tinta. Antes del duotono, `b1DuotoneFilter` aplica un **filtro de exposición**: una ganancia por foto (`photoGainFor`, ≈ 0,36 / luminancia media, tope 3, medida sobre los WebP) y un levante del negro de 0,05 (`B1_PHOTO_LIFT`) para todas. **`Modifier.photoVeil(image, intrinsic, color)`** pinta un velo liso del color del duotono sobre una zona que no debe leerse (hoy, la marca de un festival en la pantalla del celular de `twin_q09_a`), sin tocar el archivo.

### Ajustes a componentes existentes

- `ModeChip(…, style = ModeChipStyle.Solid | Tonal)`: el tonal usa `container`/`onContainer`. Úsalo dentro de una tarjeta con lomo.
- `ModeFilterChip(…, dotColor = mode.solid)`: punto de 7 dp del color del modo. Los chips de filtro son píldoras.
- `ParcheUPlusBadge(…, onHero = true)` para ponerlo sobre el héroe.
- `ParcheULinearProgress(…, color = …)`.
- `ParcheUCard` sigue igual (radio 18). Úsala para lo que no es de un modo.
- `StackedAvatars` centra las iniciales en la parte visible de cada círculo tapado y les pone un fondo opaco; `AvatarInitials` ganó `initialsEndInset` e `initialsSizeRatio` (por defecto, sin cambio).

## Qué NO hacer

- No uses `colorScheme.primary` (#6D4AFF) como color de texto: no llega a AA (`NoPrimaryAsTextTest`). El texto de marca es `prt`.
- No pongas texto con alpha sobre el héroe ni sobre plenos de modo: usa `onHero` u `onSolid` sólidos.
- No uses la mascota a menos de 24 dp ni la dibujes completa a 30 dp o menos. No le agregues antena, segundo ojo ni brazos.
- No agregues sombras ni elevación a las tarjetas: la profundidad sale del color.
- No uses el naranja fuera de la U del logo, ParcheU+ y las celebraciones.
- No toques ViewModels, navegación ni textos con lógica: el rediseño es de vista. Si un texto visible cambia, actualiza la prueba de la pantalla en el mismo commit y dilo en el PR.
- No repitas el modo solo con color: siempre va también en texto.
- No metas fotos sin duotono.

## Capturas: grabar y verificar

Las referencias se versionan junto a las pruebas (`android/{app,designsystem}/src/test/screenshots/`, ADR-0022). CI las verifica con `-Proborazzi.test.verify=true`.

```bash
export ANDROID_HOME=~/Android/Sdk
mkdir -p android/app/src/dev
cp ~/.parcheu/google-services.dev.json android/app/src/dev/google-services.json   # en .gitignore
cd android
# 1. Formatear y grabar en un solo lote (designsystem y app):
./gradlew spotlessApply
./gradlew :designsystem:testDebugUnitTest :app:testDevDebugUnitTest -Proborazzi.test.record=true
# 2. Mirar las PNG cambiadas (git status) en pares oscuro/claro antes de confirmar.
# 3. Verificar como CI (android-ci.yml):
./gradlew spotlessCheck :app:lintDevDebug :app:testDevDebugUnitTest :app:verifyProdReleaseBillingPermission \
  :designsystem:lintDebug :designsystem:testDebugUnitTest -Proborazzi.test.verify=true
```

`spotlessApply` va en su propia corrida: junto a las pruebas puede leer `build/intermediates` mientras se escriben y fallar sin motivo. Si una sola prueba de captura cambia de forma esperada, regrábala sola (`--tests`) y vuelve a correr la verificación completa.

Para grabar una sola clase, agrega `--tests '*HomeScreenshotTest*'` a la tarea de pruebas del módulo. Las diferencias de una verificación fallida quedan en `android/*/build/outputs/roborazzi/`.

## Pantallas: dónde quedó cada una

Los códigos son los de `RouteCatalog.kt` y `docs/mockups-inventario.md`. Todas heredan los tokens (fondo lila en claro, radios, Sora 800 en titulares, chips en píldora).

| Grupo | Pantalla | Cómo quedó |
|---|---|---|
| **G-A** acceso y gemelo | A1 Splash | `ParcheULogo` grande y `ParcheUAppIcon`. |
| G-A | A2-A4 Onboarding y login | Héroe por página con titular Sora 800; A4 con `PuGraph(style = ParcheVivo)` a 260 dp ("Tú" en texto: la mascota no cabe a ese tamaño). `GoogleSignInButton` sin cambio. |
| G-A | B0 Perfil básico, Edad, Suspendida | Titular `headlineLarge`; error con `StateIllustration`. |
| G-A | B1 Preguntas | Fotos en duotono con filtro de exposición y `photoVeil`; letra A/B en Sora 800 `prt`; barra de 12 segmentos; "Tu gemelo está tomando nota…" con la P conversando; error con `StateIllustration`. |
| G-A | B2-B5 Modos, Consentimiento, Gemelo listo | Héroe de modos, filas con `ModeSpineCard`; B5 con héroe y la P grande. |
| **G-B** Inicio y Conocer | C1/I1 Inicio | Referencia. |
| G-B | C2 Notificaciones | Filas por relación en `ModeSpineCard`; vacío con la P y la campana; solicitudes de ParcheU+ en `ModeSpineCard`. |
| G-B | F1 Cola de match | Héroe tonal del modo con la P conversando y el cupo del día; `ModeFilterChip(dotColor)`. |
| G-B | F2 Match encontrado | `HeroBlock(modeColors)` con `FacingTwins`. |
| G-B | F3 Escenario, F4-F5 Simulación | `FacingTwins` conversando, barra del modo, escenario en Sora 800. |
| G-B | F6 Simulaciones, F7-F8/I3 Resultados | F6 en `ModeSpineCard`; F8 con el % en `hero56` dentro de `HeroBlock(modeColors)`. |
| **G-C** Tu parche y social | D1-D5 Tu parche | `PuGraphStyle.ParcheVivo`, filtros con punto del modo, hoja de nodo con % en Sora 800. |
| G-C | D6-D9 | `ModeSpineCard` por persona o comunidad; números en Sora 800; bloqueado en gratis con `ParchePlusHero`. |
| G-C | C5 Agregar amigos | Código en un héroe, QR con `ParcheUAppIcon` al centro. |
| G-C | E1-E3 Presentar | E1 con la pareja en un bloque tonal del modo y la lista en `ModeSpineCard`; E2 con `HeroBlock(modeColors)` y las dos P; E3 con estados por modo. |
| **G-D** chats, perfil, ajustes y ParcheU+ | Lista de chats, F9 Chat | Filas con lomo del modo; aviso de perfil demo con borde discontinuo `prt`. |
| G-D | C3 Perfil, C4 Entrena a tu gemelo | Héroe con la P y la precisión en Sora 800; memorias en `ModeSpineCard`. |
| G-D | H1-H3, Borrar cuenta, Exportar, Bloqueados | Titulares `headlineLarge`; `DestructiveButton`. Borrar cuenta: con ParcheU+ concedido sin Play (`SubscriptionStatus.Granted`) dice cuándo vence y no manda a Google Play. |
| G-D | G1/I4 ParcheU+, G2, G3, G4 | Héroe con `plusGradient` y `QuotaMascots`; G2 lee el Plus del servidor; G4 con `Granted` no menciona Play. |

### Pendiente

- La P espejada del otro gemelo (`mirrored = true`) se lee como una "q" (F2-F8, D7, D8). Hay una alternativa preparada (`lookingLeft`) que falta decidir.
- E1 no tiene titular propio: agregarlo sería texto nuevo.
