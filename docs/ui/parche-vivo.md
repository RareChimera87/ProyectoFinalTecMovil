# Parche Vivo: guía para rediseñar pantallas

Dirección visual A, elegida el 13 nov 2026. La marca no cambia (violeta `#6D4AFF`, naranja `#FF8A3D`, Amistad `#2EC4A6`, Estudio `#3A86FF`, Pareja `#FF5C7A`). Lo que cambia es la jerarquía: **cada pantalla tiene un protagonista**, que puede ser un bloque de color grande, un número gigante o la mascota. La mascota nueva es **"La P que mira"**.

La base ya está en la rama `rediseno/parche-vivo`: tokens, tema, mascota, logo, ícono y componentes. Inicio (C1/I1) es la pantalla de referencia ya rediseñada (`android/app/src/main/java/me/santiagoreina/parcheu/feature/home/HomeScreen.kt`). Esta guía es para quien rediseñe los demás grupos (tabla al final).

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
    mirrored: Boolean = false               // la otra persona, mirando hacia ti
)
```

`SimulationMascot` (F4-F8, E2) y `OnboardingMascot` (A2-A4) de `:app` ya delegan en `TwinMascotGlyph`, así que esas pantallas muestran la P nueva sin otros cambios.

## Logo e ícono

- `ParcheULogo(modifier, size: WordmarkSize = Medium, textColor = colors.wordmark, mascotColor = Violet)`: lockup con la P-mascota como P de "ParcheU", "arche" en Sora 700 y la U naranja. TalkBack lo lee como "ParcheU". Inicio ya lo usa en la barra.
- `Wordmark(darkTheme, …)` sigue existiendo (texto, ahora en Sora). Úsalo solo donde la mascota sobre.
- `ParcheUAppIcon(size, modifier, background = Violet, foreground = White)`: el ícono como composable. `OnboardingAppIcon` (A1) ya delega en él.
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

### Ajustes a componentes existentes

- `ModeChip(…, style = ModeChipStyle.Solid | Tonal)`: el tonal usa `container`/`onContainer`. Úsalo dentro de una tarjeta con lomo.
- `ModeFilterChip(…, dotColor = mode.solid)`: punto de 7 dp del color del modo. Los chips de filtro son píldoras.
- `ParcheUPlusBadge(…, onHero = true)` para ponerlo sobre el héroe.
- `ParcheULinearProgress(…, color = …)`.
- `ParcheUCard` sigue igual (radio 18). Úsala para lo que no es de un modo.

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
cp ~/.parcheu/google-services.dev.json android/app/src/dev/google-services.json   # en .gitignore
cd android
# 1. Grabar en un solo lote (designsystem y app):
./gradlew spotlessApply :designsystem:testDebugUnitTest :app:testDevDebugUnitTest -Proborazzi.test.record=true
# 2. Mirar las PNG cambiadas (git status) en pares oscuro/claro antes de confirmar.
# 3. Verificar como CI:
./gradlew spotlessCheck :app:lintDevDebug :app:testDevDebugUnitTest :app:verifyProdReleaseBillingPermission \
  :designsystem:lintDebug :designsystem:testDebugUnitTest -Proborazzi.test.verify=true
```

Para grabar una sola clase, agrega `--tests '*HomeScreenshotTest*'` a la tarea de pruebas del módulo. Las diferencias de una verificación fallida quedan en `android/*/build/outputs/roborazzi/`.

## Pantalla → qué cambia

Los códigos son los de `RouteCatalog.kt` y `docs/mockups-inventario.md`. Todas heredan ya los tokens (fondo lila en claro, radios, Sora 800 en titulares, chips en píldora). La tabla dice qué falta adoptar.

| Grupo | Pantalla | Qué cambia |
|---|---|---|
| **G-A** acceso y gemelo | A1 Splash | Cambiar `OnboardingWordmark` por `ParcheULogo(size = Large)`; el ícono ya es `ParcheUAppIcon`. |
| G-A | A2-A4 Onboarding y login | `TwinIllustration` ya usa la P; agregar un héroe por página (titular Sora 800). En A4, `GoogleSignInButton` se queda. |
| G-A | B0 Perfil básico, Edad, Suspendida | Titular `headlineLarge`; campos sin cambio; la mascota de error en gris. |
| G-A | B1 Preguntas | Las fotos de opción pasan a `DuotonePhoto(mode = …)` (radio 20). La letra A/B va en Sora 800 22 `prt`, la opción elegida con borde de 2 dp `prt` y la barra de 12 segmentos violeta. "Tu gemelo está tomando nota…" con `TwinMascot(S, talking)`. |
| G-A | B2-B3 Modos | Cada modo como `HeroBlock(colors = HeroBlockDefaults.modeColors(mode))` o `ModeSpineCard`; `ModeChip` tonal. |
| G-A | B4 Consentimiento, B5 Gemelo listo | B5 con un héroe y `TwinMascot(XL)` grande; botón primario abajo. |
| **G-B** Inicio y Conocer | C1/I1 Inicio | **Hecha** (referencia). |
| G-B | C2 Notificaciones | Filas por relación en `ModeSpineCard`; vacío con mascota. |
| G-B | F1 Cola de match | Héroe con la mascota conversando (`talking`) y "Te queda 1 hoy"; el modo como `ModeFilterChip(dotColor)`. |
| G-B | F2 Match encontrado | `HeroBlock(colors = modeColors(mode))` con las dos P frente a frente (`mirrored = true` para el otro). |
| G-B | F3 Escenario, F4-F5 Simulación | Las dos P se acercan y conversan (`TwinMascotGlyph(mouth = Talking)` y `mirrored`); barra del color del modo; escenario actual en Sora 800. |
| G-B | F6 Simulaciones, F7-F8/I3 Resultados | F6 como lista de `ModeSpineCard`. En F8, el % de afinidad como número héroe (`hero56`) dentro de `HeroBlock(modeColors)`. |
| **G-C** Tu parche y social | D1-D5/I2 Tu parche (grafo) | Filtros `ModeFilterChip(dotColor)`. En `PuGraph`, "Tú" con `TwinMascotGlyph` blanca sobre violeta y anillos rotulados ("cercanos", "por conocer"). Hoja de nodo con % en Sora 800 30. |
| G-C | D6-D9 Amigos de amigos, Comunidades, Evolución | `ModeSpineCard` por persona; números de estadística en Sora 800. |
| G-C | C5 Agregar amigos, invitaciones y código | El código en un `HeroBlock` (Headline); atajos con `AccessTile`. |
| G-C | E1-E3 Presentar | `AccessTile` para elegir a los dos; E2 con `HeroBlock(modeColors)` y las dos P. |
| **G-D** chats, perfil, ajustes y ParcheU+ | Lista de chats, F9 Chat | Filas con lomo del modo; aviso de perfil demo con borde discontinuo `prt`; burbujas sin cambio de lógica. |
| G-D | C3 Perfil, C4 Entrena a tu gemelo | Héroe con `TwinMascot(L)` y precisión en Sora 800; memorias en `ModeSpineCard` por modo. |
| G-D | H1-H3, Borrado, Exportar, Bloqueados | Solo tokens: titulares `headlineLarge`; acciones destructivas en `error`. |
| G-D | G1/I4 ParcheU+, G3, G4, LimitSheet | Héroe con `plusGradient`; `ParcheUPlusBadge(onHero = true)` sobre violeta; G3 con celebración y la P. |
| G-D | Vacíos y errores globales | `EmptyState`/`ErrorState` de `components/compound/States.kt`: mascota más un objeto de contexto (cuaderno, taza, mapa) y texto de qué va a pasar; error con `TwinMascot(error = true)`. |
