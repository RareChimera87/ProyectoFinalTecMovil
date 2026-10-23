# ParcheU: inventario maestro de mockups

Fuente: 12 archivos de Claude Design (`ParcheU Mockups`, `PU NavBar`, `PU Grafo`, `PU Seccion A` a `I`). Resolución objetivo: Android 360x800, Material 3, oscuro principal, claro al final. Los ids de pantalla son estables: **sección + número**. La columna "ID mockup" conserva la numeración original del diseño (01, 02a, 08a...). Las secciones del archivo maestro son: A Arranque, B Creación del gemelo, C Inicio/Perfil, D Tu parche, E Presentar, F Conocer, G ParcheU+, H Seguridad/Privacidad, I Modo claro.

**Actualización 2026-10-23.** Fuente: `PU Seccion H.dc.html` y `ParcheU Mockups.dc.html` (proyecto de Claude Design 5d7b35fb). La sección H pasa de 3 a 5 pantallas: se agregan **H4 Exportar mis datos (24a)** y **H5 Borrar cuenta (24b)**, y se modifica **H3 Ajustes de privacidad (23)** (nuevo enlace 'Exportar mis datos' y destinos 24a/24b). El maestro sigue importando las mismas 9 secciones; el `hint-size` de la sección H es 2184px (cinco teléfonos). Las demás secciones, `PU Grafo`, `PU NavBar` y `support.js` no cambiaron desde la versión del 2026-10-22 (mismas etags). Detalle en la sección 1.1.

## 1. Tabla de pantallas

Leyenda premium: **No** = gratis; **Sí** = solo ParcheU+; **Bloqueo** = pantalla gratis que muestra el candado/upsell.

### Sistema y componentes compartidos (no son pantallas de la app)

| ID | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|
| S0 | Hoja de sistema (índice, paleta, tipografía) | No | Estática | A..I |
| N0 | PU NavBar (barra inferior, prop `active`) | No | active = inicio / parche / conocer / chats / perfil; sin badge de no leídos | Inicio (C1), Parche (D1), Conocer (F1), Chats (sin pantalla de lista diseñada), Perfil (C3) |
| G0 | PU Grafo (componente; props size, filter, selected, fof, count) | No (fof es ParcheU+) | todos / filtrado / seleccionado / fof / miniatura (size<200) / count reducido | Nodo -> D2 |

### A. Arranque

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| A1 | 01 | Splash | No | cargando; con sesión; sin sesión | A2 (sin sesión), C1 (con sesión; el mockup dice "04") |
| A2 | 02a | Onboarding 1, Crea tu gemelo digital | No | único (paso 1/3) | A3, A4 (Saltar) |
| A3 | 02b | Onboarding 2, Sus gemelos conversan primero | No | único (paso 2/3) | A4 |
| A4 | 02c | Onboarding 3, Arma tu parche + Continuar con Google | No | único (sin cargando ni error de login) | B1 |

### B. Creación del gemelo

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| B1 | 03a | Preguntas (12, tarjetas swipe) | No | opción A / B / "Depende"; progreso 4 de 12 | B2 (al terminar); close sin destino |
| B2 | 03b | Elegir modos (switches) | No | cada switch on/off; sin validación de 0 modos | B3 (Pareja), B4, atrás B1 |
| B3 | 03c | Confirmación +18 para Pareja (diálogo) | No | fecha + checkbox; sin error ni cargando | B2 (Confirmar = Pareja ON, Cancelar = OFF) |
| B4 | 03d | Consentimiento de aprender de chats | No | switch on/off | B5; atrás B2 |
| B5 | 03e | Gemelo listo (celebración) | No | estático | D1 (Ver mi parche), C1 (Ir al inicio) |

### C. Inicio, notificaciones, perfil

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| C1 | 04 | Inicio | Bloqueo (chip ParcheU+) | gratis 3 de 5; en curso 60 %; resultado listo; faltan vacío, 0 restantes, ParcheU+ | C2, F4, F7/F8, F1, G1 (chip), NavBar |
| C2 | 05 | Notificaciones | Bloqueo (item "quieren conectar contigo") | leída / no leída; bloqueado; falta vacío | F7/F8, E3, G1, C4, atrás C1 |
| C3 | 06 | Perfil y gemelo digital | No | gemelo con 4 meses de historial; falta recién creado | C4, H3 (engranaje), G4 (suscripción, sin disparador visible) |
| C4 | 06b | Entrena a tu gemelo | No | memoria pendiente / aceptada; respuesta por calificar | C3 |
| C5 | 07 | Agregar amigos, QR y código | No | tab "Mi código"; tab "Escanear" no dibujado | share sheet, cámara, invitación |

### D. Tu parche

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| D1 | 08a | Tu parche, grafo | Bloqueo (banner amigos de amigos y comunidades) | cargado; filtro por modo; falta versión ParcheU+ | D2, D9, C5, G1, NavBar |
| D2 | 08b | Nodo seleccionado (bottom sheet) | No | sheet abierto; faltan vínculos simularon/pendiente | F4, F9, D1 |
| D3 | 08c | Tu parche, vacío | No | vacío | C5, F1 |
| D4 | 08d | Tu parche, cargando | No | skeleton animado | D1, D5 |
| D5 | 08e | Tu parche, error | No | error de red | D4 (Reintentar) |
| D6 | 09a | Amigos de amigos | Sí | desbloqueado | D1, F4 (Simular), "Pedir presentación" (sin pantalla) |
| D7 | 09b | Amigos de amigos, gratis | Bloqueo | borroso + candado | G1, D1 |
| D8 | 10 | Comunidades, tus parches | Sí | con comunidades; faltan bloqueado y vacío | D1 filtrado |
| D9 | 11 | Evolución de la red | Sí | con historial; faltan bloqueado y <2 meses | D1 (snapshot del mes) |

### E. Presentar a dos amigos

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| E1 | 12a | Elegir modo y dos amigos | No (consume cupo) | 2 de 2; faltan 0/1 elegidos, límite alcanzado | E2 |
| E2 | 12b | Resultado de sus gemelos | No | cargado; falta animación intermedia | E3 (envía propuesta), "No por ahora" |
| E3 | 12c | Propuesta recibida | No | pendiente; faltan aceptado/rechazado | F9 (cuando ambos aceptan) |

### F. Conocer (match al azar, escenarios, simulación, resultados, chat)

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| F1 | 13a | Match al azar, en la fila | No (1/día gratis) | buscando (animado) | F2, C1 (cancelar) |
| F2 | 13b | Match encontrado | No | Escenario / Simulación | F3, F4 |
| F3 | 14 | Modo escenario, chat | No | en curso con temporizador y presupuesto; falta acuerdo | F7/F8, H1 (flag) |
| F4 | 15a | Simulación en curso | No | en curso, segundo plano | F7/F8, F5 |
| F5 | 15b | Simulación, error | No | error (no descuenta cupo) | F4, C1 |
| F6 | 15c | Simulaciones, vacío | No | vacío (5 disponibles) | D1, F1 |
| F7 | 16a | Resultados, versión gratis | Bloqueo | informe borroso con candado | F9 (Conectar), G1 |
| F8 | 16b | Resultados, versión ParcheU+ | Sí | informe completo | F9, F4 |
| F9 | 17 | Chat en vivo | No | conectados; banner de consentimiento | H1 (menú), F4, ajustes |

### G. ParcheU+

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| G1 | 18 | Paywall | No | anual (default) / mensual; faltan cargando precios, error | Play Billing -> G3, cerrar, restaurar |
| G2 | 19 | Límite alcanzado (bottom sheet sobre Inicio) | Bloqueo | único | G1, C1 |
| G3 | 20 | Compra exitosa | Sí | anual; falta mensual y verificación pendiente | D6, C1 |
| G4 | 21 | Gestionar suscripción | Sí | activa anual; faltan cancelada, gracia, mensual | atrás, Play (cambiar/restaurar/cancelar), historial (sin diseñar) |

### H. Seguridad y privacidad

| ID | ID mockup | Nombre | Premium | Estados | Navega a |
|---|---|---|---|---|---|
| H1 | 22a | Reportar o bloquear (bottom sheet sobre chat) | No (gratis siempre) | único; falta confirmación de bloqueo | H2, confirmación de bloqueo (sin diseñar), F9 |
| H2 | 22b | Reportar, motivos | No | motivo 1 + ambos checkbox marcados (estático) | confirmación (sin diseñar), close |
| H3 | 23 | Ajustes de privacidad (modificada 2026-10-23) | No | toggles de modos, visibilidad a/b/c, aprender de chats | B3 (activar Pareja), H4 (Exportar mis datos), H5 (Borrar mi gemelo y mis datos) |
| H4 | 24a | Exportar mis datos (nueva 2026-10-23) | No | 4 de 5 categorías marcadas (Chats sin marcar), formato JSON seleccionado; faltan enviando, enviado y error | atrás H3; 'Exportar mis datos' -> enlace al correo (confirmación sin diseñar); la anotación dice 'Borrar mi cuenta -> 24b' pero la pantalla no tiene ese botón |
| H5 | 24b | Borrar cuenta (nueva 2026-10-23) | No | campo de confirmación lleno con 'BORRAR' (botón habilitado); faltan vacío/deshabilitado, borrando y error | atrás H3; 'Exportar' -> H4; 'Borrar mi cuenta' -> cierra sesión -> A1 |

### 1.1 Detalle de la sección H actualizada (2026-10-23)

Las tres pantallas usan el tema oscuro de la sección (bg #14111F, s1 #1F1B2E, s2 #2B2641, ln #3A3354, tx #F5F2FB, mu #B0A9C6, prt #B6A6FF, prc #2E2566, onprc #E0D9FF, coralt #FF8DA2). Ninguna tiene variante en modo claro (Sección I no las incluye): en claro se aplican los tokens de 3.2 (coralt #C42C4A, prt #5533E8, prc #ECE7FF, onprc #2E1A8C). Ninguna muestra NavBar (pantallas hijas con back).

**H3 · Ajustes de privacidad (23), modificada.** Feature: E8.2 (enlaces a H4/H5 de E8.3).
- Anotación: "Modos activos, visibilidad en el grafo y aprendizaje. Todo funciona." · "Activar Pareja → 03c · Exportar → 24a · Borrar → 24b".
- TopAppBar hija: arrow_back + "Privacidad" (Sora 19/600).
- SectionHeader "MODOS ACTIVOS" + Card s1 radio 20 con 3 filas de Switch (52x32): caja de icono 36 radio 12 en color pleno del modo (diversity_3 #2EC4A6, favorite #FF5C7A, school #3A86FF; icono #14111F) + nombre 15/500. Estado del mockup: los tres ON.
- SectionHeader "VISIBILIDAD EN EL GRAFO DE OTROS" + Card con 3 RadioRow: "Visible con mi nombre" / "Los amigos de tus amigos ven tu nombre y foto"; "Aparecer como «Alguien»" / "Solo ven tu % de afinidad" (seleccionada por defecto, `vis: 'b'`); "Oculto" / "Solo tus contactos directos te ven".
- InfoBanner tonal prc/onprc radio 16, icono lock: "Tus vínculos de Pareja nunca aparecen en el grafo de otras personas."
- Fila con Switch "Aprender de mis chats" / "Tu gemelo aprende de cómo conversas" (ON).
- Enlaces de texto al final: "Borrar mi gemelo y mis datos" (14/600 coralt) → H5 y, **nuevo**, "Exportar mis datos" (14/600 prt) → H4.
- Cambio respecto a la versión del 2026-10-22: solo el enlace "Exportar mis datos" y la anotación de navegación (antes el borrado iba a una pantalla sin diseñar). Sigue sin las filas 'Personas bloqueadas', 'Cerrar sesión' ni enlaces a Términos/Política que pide E8.2.
- Estados: sin vacío, cargando ni error.

**H4 · Exportar mis datos (24a), nueva.** Feature: E8.3 (entrada desde H3, E8.2).
- Anotación: "Descarga una copia de tu información. Se llega desde Privacidad (23)." · "Exportar → enlace al correo · Borrar mi cuenta → 24b".
- TopAppBar hija: arrow_back + "Exportar mis datos".
- Texto de introducción 14 mu: "Elige qué incluir. Te mandamos un enlace de descarga al correo en unos minutos."
- Card s1 radio 20 con 5 filas seleccionables (min 58, gap 14): caja de icono 40 radio 12 fondo s2 con icono 22 prt + título 15/500 + subtítulo 12.5 mu + Checkbox 22 radio 7. Filas: person "Perfil y modos" / "Respuestas de personalidad" (marcada); smart_toy "Tu gemelo" / "Nivel 6 · 24 aprendizajes" (marcada); hub "Tu parche" / "9 conexiones y comunidades" (marcada); insights "Simulaciones" / "14 resultados" (marcada); chat_bubble "Chats" / "Solo tus mensajes" (sin marcar: borde 2 mu).
- SectionHeader "FORMATO" + selector de 2 opciones en grid 1fr 1fr gap 10, alto 48 radio 14: "JSON" seleccionado (borde 2 #6D4AFF, fondo prc, texto onprc 14/600) y "PDF legible" (borde 1.5 ln).
- InfoNote s1 radio 16 con icono mail prt: "Va a mariana.gomez@correo.uis.edu.co · el enlace dura 7 días." (correo y vigencia vienen del backend).
- Footer: PrimaryButton 52 con icono download "Exportar mis datos".
- Estados: solo el estático; faltan enviando, confirmación 'te enviamos el enlace', error, y validación de 0 categorías.

**H5 · Borrar cuenta (24b), nueva.** Feature: E8.3 (entrada desde H3, E8.2; también la pantalla de bloqueo por menor de edad de E2.6 ofrece 'Borrar mi cuenta').
- Anotación: "Borra la cuenta, el gemelo y todos los datos. Pide confirmación escrita." · "Borrar → cierra sesión → 01 · Exportar antes → 24a".
- TopAppBar hija: arrow_back + "Borrar cuenta".
- Icono hero: caja 64 radio 20 fondo s1 con delete_forever 34 coralt, centrada.
- Título Sora 24/700 ls -.4: "¿Borrar tu cuenta para siempre?"; cuerpo 14 mu: "Esto no se puede deshacer. Se borra:".
- Card s1 radio 20 (lista, filas 42, icono 20 coralt): smart_toy "Tu gemelo y todo lo que aprendió"; hub "Tu parche y tus 9 conexiones"; insights "14 simulaciones y sus informes"; chat_bubble "Tus chats".
- Fila outline (borde 1.5 ln, radio 16) con icono download prt: "¿Quieres una copia antes?" + acción de texto "Exportar" 13/600 prt → H4.
- InfoNote s1 radio 16 con icono info mu: "Si tienes ParcheU+, cancélalo en Google Play; borrar la cuenta no cancela el cobro."
- Confirmación escrita: "Escribe **BORRAR** para confirmar" (13 mu, palabra en tx 600) + campo 52 radio 14 con borde 2 coralt, texto Sora 16/600 ls 1 (valor del mockup: "BORRAR").
- Footer: botón destructivo 52 radio 16 fondo #FF5C7A (coral pleno) texto #14111F 15/600 "Borrar mi cuenta" + nota 12 mu centrada "Tienes 14 días para arrepentirte iniciando sesión."
- Navegación: Borrar → cierra sesión → A1 (sin sesión → A2/A4).
- Estados: solo el habilitado; faltan campo vacío con botón deshabilitado, borrando, error y la pantalla de 'cuenta en periodo de arrepentimiento' al volver a iniciar sesión.

### I. Modo claro (mismas pantallas sobre Hueso)

| ID | ID mockup | Equivale a | Premium |
|---|---|---|---|
| I1 | 04·C | C1 Inicio | No |
| I2 | 08a·C | D1 Grafo | Bloqueo |
| I3 | 16b·C | F8 Resultados ParcheU+ | Sí |
| I4 | 18·C | G1 Paywall | No |

Sección I no agrega flujos nuevos: solo exige un segundo `lightColorScheme`.

## 2. Catálogo de componentes

| Componente | Especificación consolidada | Pantallas |
|---|---|---|
| PhoneFrame / StatusBar / HomeIndicator | Solo mockup (376x816, borde 8, radio 36). No implementar; en Compose usar Scaffold con insets del sistema | todas |
| TopAppBar | 56dp; icon buttons 48x48; título Sora 19/600. Variantes: Inicio (wordmark + campana + avatar 34), raíz (título + hasta 2 iconos), hija (back/close + título + acción de texto o badge), chat (avatar + nombre + subtítulo + more_vert/flag) | A2-A4 (solo wordmark + Saltar), B1-B4, C1-C5, D1-D9, E1-E3, F1-F9, G4, H1-H3, I1-I3 |
| NavBar (PU NavBar) | 76dp, fondo s1, borde superior 1 ln, 5 ítems (home, hub, shuffle, chat_bubble, person). Activo: píldora 56x30 radio 15 prc, icono relleno onprc, label 11/600. Inactivo: mu, 11/500 | C1, C3, D1, D3, D4, D5, D7, D8, G2 (fondo), I1, I2 |
| PrimaryButton | 52dp, radio 16, #6D4AFF, texto blanco 15/600, icono 20 opcional. Variante 36dp radio 12 | A2, A3, B2-B5, C4, C5, D2-D5, E1-E3, F1-F8, G1-G3, H2, H4, I1, I3 |
| DestructiveButton | 52dp, radio 16, fondo #FF5C7A (coral pleno), texto #14111F 15/600; nota 12 mu centrada debajo | H5 |
| TonalButton | prc / onprc, 52 / 40 / 36dp | C1, C3, C5, D2, D7, F4, F7, I1 |
| OutlineButton | borde 1.5 ln, texto tx | C4, F1, F7, F8, I3 |
| TextButton | prt, 52dp, 15/600; acción de texto 13/600 | A2, A3, B3, B5, D3, E2, E3, F5, F6, G2, G3, C1-C4 |
| GoogleSignInButton | 52dp, fondo #FFFFFF, texto #1A1626, "G" placeholder (usar asset oficial) | A4 |
| Wordmark ParcheU | Sora 700, "U" #FF8A3D; tamaños 20, 22, 44 (88 solo en hoja). "Parche" blanco en oscuro, #6D4AFF en claro | S0, A1-A4, C1, G2, I1 |
| AppIcon | 96x96 rx 26, #6D4AFF, polilínea en U blanca al 70 %, 5 nodos (inferior #FF8A3D) | S0, A1, C5 (QR) |
| TwinMascot (gemelo) | Blob 48% 52% 44% 46% / 56% 54% 46% 44%, antena, ojos y boca #14111F (.85). Color: violeta = usuario, color del modo = otro, blanco sobre degradado. Alto = 1.125 x ancho. Tamaños 26 a 120; estado error opacity .5 + grayscale | S0, A2, A3, B1, B4, B5, C1, C3, D3, E2, F1-F6, F7, F8, G1-G3, I1, I3 |
| ModeChip / ModePill | Pill radio 999, fondo color de modo, texto #14111F, icono. Tamaños en conflicto: 28dp (A, B, E) y 24dp (C, D, F, G, I). Ver sección 3 | B5, C1, C3, D2, D8, E3, F2, F7, F8, G2, I1, I3 |
| ModeFilterChip | 32dp radio 12; activo prc/onprc, inactivo borde 1 ln; Todos/Amistad/Pareja/Estudio | D1, D2, I2 |
| SecondaryFilterChip | Outline 32dp: "Afinidad 70 %+", "Comunidad" (sin estado on ni lógica) | D1, D2, I2 |
| ModeSegmentedControl | 44dp (2 modos, E1) o 48dp (3 modos, F1), radio 14/16, activo con color del modo y texto oscuro | E1, F1 |
| SegmentedControl neutro | 44dp, activo prc/onprc ("Mi código" / "Escanear") | C5 |
| InterestChip | Outline 32dp, radio 12, icono 16 | F2 |
| PillChip (gemelo aprende) | Fondo s2, 12/600; variante afinidad prc/onprc 13/600 | A2, A3, E3 |
| ParcheUPlusBadge/Chip | Pill 22 (26 en G4), borde 1.5 degradado 135deg #6D4AFF a #FF8A3D, "ParcheU" Sora 11/700, "+" en ort | C1, D1, D6, D8, D9, F8, G2, G4, I1-I3 |
| LockedFeatureBanner | Borde dashed ln, radio 16, candado + texto + badge | D1, I2 |
| LockedOverlay (borroso) | blur 6px, opacity .55, círculo 44 con lock, título 15/600, botón tonal "Ver ParcheU+" | D7, F7 |
| Card (superficie) | s1, radio 20, padding 16, gap 10. Variantes: stat (radio 16, 12x14), sugerencia (14), plan activo (radio 24, borde degradado) | casi todas |
| SectionHeader | 12/600, uppercase, ls .6, mu; acción opcional 13/600 prt | B4, C1-C4, E1, E2, F2, F8, H3, H4, I1, I3 |
| AvatarInitials | Círculo; fondo rgba(109,74,255, alpha de afinidad); tamaños 26/32/34/36/40/48/56/60/64/72; anónimo: s2 + borde dashed prt + icono person | C1-C4, D2, D6, D8, E1-E3, F9, H1, I1 |
| StackedAvatars | Solape -10 (-12 en E3), borde 2-3 | D8, E3 |
| AffinityBubble / escala | Violeta con alpha por afinidad; "nunca rojo/verde"; etiquetas por descubrir / media / alta | S0, G0, avatares |
| AffinityRing | SVG r 50.5 (112) o r 54 (120), stroke 11-12, linecap round, % Sora 29-31/700 | E2, F7, F8, I3 |
| GraphEdgeLegend | sólida 2.5 conectados / dash 4 3 2 simularon / 1.5 op .35 pendiente | S0, D1, D2 |
| StatCard | s1 radio 16, número Sora 20/700 | D1, I2 |
| NodeDetailBottomSheet | s1 radio 28 28 0 0, handle 36x4, scrim | D2 |
| BottomSheet genérico | Igual; padding 10 20 30 | D2, G2, H1 |
| ModalDialog + Scrim | s1 radio 28, margen lateral 24, scrim rgba(5,3,12,.62) | B3 |
| Switch | 52x32 radio 16. ON: #6D4AFF + thumb blanco 24 con check. OFF: s2, borde 2 mu, thumb 16 mu | B2, B4, H3 |
| Checkbox | 22x22 radio 7, #6D4AFF + check blanco; off: borde 2 mu | B3, E1, H2, H4 |
| DataCategoryRow | min 58, caja de icono 40 radio 12 fondo s2 con icono 22 prt, título 15/500 + subtítulo 12.5 mu, Checkbox a la derecha; dentro de Card s1 con divisores ln | H4 |
| FormatSelector | Grid 2 columnas gap 10, celdas 48dp radio 14, 14/600; seleccionada borde 2 #6D4AFF + prc/onprc, otra borde 1.5 ln | H4 |
| ConsequenceList | Card s1 radio 20, filas 42 con icono 20 coralt + texto 14 (qué se borra) | H5 |
| RadioRow | 22, borde 2 (#6D4AFF / mu), punto 10 | G1, H2, H3 |
| OutlinedTextField | 56dp radio 14 (fecha), 52dp radio 16 (código), TextArea 96dp; variante de confirmación destructiva 52dp radio 14 borde 2 coralt, Sora 16/600 ls 1 | B3, C5, H2, H5 |
| LinearProgress / SegmentedQuota | 6dp radio 3 (track s2, fill #6D4AFF); cuota: 5 píldoras 8dp radio 4 | B1, C1, C3, F3, F4, G2 |
| QuestionCardStack + OptionCard + SwipeHintBar | Pila de 3 tarjetas (rot -3 / +2), opción con borde 2 | B1 |
| InfoBanner / InfoNote / ConsentBanner | Tonal prc/onprc radio 16, o s2 radio 12, o s1 radio 12 (s1 radio 16 en H4/H5); variante outline borde 1.5 ln radio 16 con acción de texto prt (H5 'Exportar') | B2, B3, B4, E1, E2, F9, H1, H3, H4, H5 |
| InfoListCard | Encabezado + filas con icono prt/mu | B4, E2, E3, F8 |
| ChatBubble + ChatInputBar | Ajena s2 radio 20 20 20 6; propia #6D4AFF radio 20 20 6 20; gemelo prc/onprc radio 18 18 6 18; input pill 48 + FAB send 48 | A3, C4, E2, F3, F4, F9, H1 |
| ScenarioPinnedCard | prc/onprc, temporizador, barra de presupuesto | F3 |
| SimulationStage + TypingIndicator | 300dp radio 28; animaciones puB1/puB2/puDot | F4 |
| ScenarioChecklist | check_circle / progress_activity / radio_button_unchecked | F4 |
| ChoiceCard | radio 20, borde 2 (#6D4AFF + prc seleccionada) | F2 |
| FriendSelectRow, PairPreview | Fila 60dp con checkbox; vista del par con handshake | E1 |
| FriendOfFriendCard, CommunityCard, MonthSnapshotCard, MomentsTimeline | Ver pantallas D6, D8, D9 | D6, D8, D9 |
| EmptyState / ErrorState / GraphSkeleton | Centrado, Sora 22/700, CTA 52dp | D3-D5, F5, F6 |
| DestructiveHeroIcon | Caja 64 radio 20 fondo s1, icono 34 coralt (delete_forever) | H5 |
| NotificationRow, IconCircle | Fila 12 gap, punto no leído 8 | C2, C1 |
| LineChart (precisión del gemelo) | SVG 296x96 | C3 |
| QRCard | Tarjeta blanca radio 24, QR 200 con logo | C5 |
| SettingsRow | min 56, caja icono 40 radio 12; destructiva en coralt; enlace de texto 14/600 (coralt para borrar, prt para exportar) | G4, H1, H3 |
| PlanCard, ComparisonTable, PaywallHeroHeader | Ver G1 | G1, I4 |
| GradientBorderCard | radio 24, borde 1.5 degradado | G4 |
| CelebrationBackground + Confetti | radial naranja/violeta, 6 piezas | B5, G3 |
| DecisionFooter | "Ahora no" + "Conectar" + nota | F7, F8, I3 |
| LoadingDots, PageIndicator | 3 puntos 8px; 3 páginas (22x8 activa) | A1; A2-A4 |

## 3. Tokens de diseño definitivos

### 3.1 Color, tema oscuro (principal)

| Rol | Hex | Uso |
|---|---|---|
| primary / Violeta | #6D4AFF | Botones primarios, switch ON, progreso, nodo "Tú", base de la escala de afinidad |
| background / Tinta (bg) | #14111F | Fondo; también texto/íconos sobre colores de modo y rasgos del gemelo |
| surface1 (s1) | #1F1B2E | Tarjetas, NavBar, sheets |
| surface2 (s2) | #2B2641 | Chips neutros, inputs, tracks, burbuja ajena |
| outline (ln) | #3A3354 | Bordes, divisores, anillos del grafo |
| onSurface (tx) | #F5F2FB | Texto principal |
| onSurfaceVariant (mu) | #B0A9C6 | Texto secundario |
| primaryTint (prt) | #B6A6FF | Texto/links sobre oscuro |
| primaryContainer (prc) | #2E2566 | Chips activos, botón tonal, NavBar activo |
| onPrimaryContainer (onprc) | #E0D9FF | Texto sobre prc |
| Amistad / Menta | #2EC4A6 (tint #5FD9C0) | Modo Amistad |
| Pareja / Coral | #FF5C7A (tint #FF8DA2) | Modo Pareja; el tint se usa también como color "destructivo" (coralt) |
| Estudio / Azul eléctrico | #3A86FF (tint #82B1FF) | Modo Estudio |
| accent / Naranja | #FF8A3D (tint #FFA266) | Solo la U del logo, ParcheU+ y celebraciones |
| plusGradient | linear 135deg #6D4AFF a #FF8A3D | Solo momentos ParcheU+ |
| paywall hero | linear 160deg #6D4AFF 0 %, #8E5BD9 55 %, #FF8A3D 130 % | Header del paywall |
| white | #FFFFFF | Texto sobre violeta pleno, wordmark |
| scrim | rgba(5,3,12,.62) | Sheets y diálogos |
| celebración | radial rgba(255,138,61,.18-.22) a rgba(109,74,255,.16-.18) a transparente | B5, G3 |
| Google button | fondo #FFFFFF, texto #1A1626, "G" #3A6FE0 sobre #F1ECE4 (placeholder) | A4 |
| QR name | #1A1626 sobre blanco | C5 |

No son de la app: frame #000000 y canvas #0B0912 (tema oscuro del lienzo).

### 3.2 Color, tema claro (solo definido en Sección I)

bg Hueso #FAF7F2, s1 #FFFFFF, s2 #F1ECE4, ln #E2DBCF, tx #1A1626, mu #5C566E, prt #5533E8, prc #ECE7FF, onprc #2E1A8C, primary #6D4AFF (igual), texto de modo: mintt #0E7A65, coralt #C42C4A, bluet #1F5FD1, ort #B8560F; los colores plenos de modo, naranja #FF8A3D y texto sobre chips #14111F no cambian. Scrim rgba(20,17,31,.45). No son de la app: frame #1A1626, canvas #EDE7DC.

### 3.3 Escala de afinidad (violeta con alpha)

Fórmula definitiva (la de PU Grafo, que se usa en nodos y avatares): `alpha = clamp(.22 + .78 * (afinidad - 50) / 45, .22, 1)`. Texto blanco si alpha > .55, si no tx. La tabla de la hoja de sistema (52 a .25, 64 a .46, 76 a .67, 88 a .88, 95 a 1.0) es ilustrativa y difiere ligeramente (ver conflictos). Etiquetas por descubrir / media / alta: umbrales sin definir.

### 3.4 Tipografía

- Sora (400/600/700) para títulos, números, iniciales, precios. Inter (400/500/600/700) para interfaz (14 a 16). Iconos: Material Symbols Rounded (peso 400; FILL 1 solo en NavBar activo y workspace_premium).
- Escala: display 44 (splash) y 88 (hoja); Sora 34/32 hoja; Sora 31/29 afinidad; Sora 28-27 títulos de hero; Sora 26/24/22/21/20 títulos de pantalla y estados; Sora 19/600 título de app bar; Sora 17-14 nombres; Inter 16/15/14 cuerpo y botones (15/600); 13 a 11 secundarios y chips; overline 12/600 uppercase ls .6.
- Fuentes no vienen con Android: empaquetar Sora e Inter (OFL) y mapear los iconos a material-icons-extended o incluir la fuente.

### 3.5 Espaciado y tamaños

Gutter 16 (24 en onboarding y B5/G3). Gap entre bloques 14. Padding de card 16. Footer de acciones 12/16/28. Status bar 28, app bar 56, NavBar 76 (usar insets, no 18 fijo), botón 52 (40 y 36 compactos), icon button 48x48, chip 32 (filtro), 28 o 24 (modo), switch 52x32, campo 56.

### 3.6 Radios

Chips y pills 999; botones 52dp 16; botones compactos 12; cards 20; tarjetas hero 24-28; bottom sheet 28 28 0 0; campo 14-16; checkbox 7; píldora NavBar 15; barras de progreso 3-4; avatares y nodos 50 %.

### 3.7 Grafo (G0)

Anillos dashed `2 5`, 1px ln, radios 62/102/140 (con fof 48/80/108 + 140), k = size/328. Nodo central r 25 #6D4AFF. Nodos r 18/16/14. Líneas: conectados 2.5 sólida .95; simularon 2 dash `5 5` .95; pendiente 1.5 op .3 (nodo .7). Atenuado: línea .1, nodo .22. Seleccionado: halo tx 2px a r+6. Amigos de amigos: r 13, fondo s2, borde prt 1.5 (dashed `3 3` si anónimo), línea prt dash `2 4` .8.

## 4. Conflictos de tokens detectados

| Tema | Valores en conflicto | Resolución propuesta |
|---|---|---|
| Alpha de afinidad | Hoja de sistema: 52 a .25, 64 a .46, 76 a .67, 88 a .88, 95 a 1.0. PU Grafo: fórmula .22 + .78(a-50)/45 (50 a .22, 64 a .46, 76 a .79...). Avatares sueltos usan .58, .71, .76, .77, .81 | Usar la fórmula del componente en todo; la tabla de la hoja es solo ilustración |
| Tamaño de ModeChip | 28dp, padding 0 11 0 8, icono 16 (S0, A, B, E) frente a 24dp, padding 0 9 0 6, icono 14, texto 11 (C, D, F, G, I) | Definir dos tamaños: M (28, hero y selección) y S (24, dentro de listas y tarjetas) |
| Contraste texto #14111F sobre #3A86FF | Estimado ~4.6:1 (S0), ~5.3:1 (D), ~4.0:1 (I) | Medir con la fuente final; si no pasa AA, usar blanco solo en Estudio |
| "prt" claro vs oscuro | #B6A6FF (oscuro) frente a #5533E8 (claro) | No es error: es el mismo rol por tema |
| Wordmark | "Parche" blanco en oscuro y #6D4AFF en claro | Por tema |
| Interpolación de afinidad en avatar | Avatares de 40px usan alpha fijo (.71/.72/.95) sin fórmula | Derivar de la fórmula |
| Pareja coral vs error | #FF8DA2 (coralt) es color de modo Pareja y color de acciones destructivas (Reportar, Bloquear, Cancelar suscripción, Borrar). Desde 2026-10-23 H5 usa además el coral **pleno** #FF5C7A como fondo del botón 'Borrar mi cuenta' y borde coralt en el campo de confirmación | Decidir si se separa un token `error`/`errorContainer`; si no, documentar que `destructive` = coral pleno con texto #14111F |
| Color de texto en botón/mascota del paywall | Mascota blanca solo en header | Token puntual, no global |
| Padding de footer | 12 16 28 frente a 12 24 32 en B5 y G3 (pantallas de celebración) | Mantener la variante 24/32 solo en celebración |
| Dimensión de mascota | 26 a 120 sin escala única | Usar proporción 1:1.125 y tamaños S 30, M 52-64, L 72-84, XL 112-120 |

## 5. Flujos end-to-end

1. **Primer uso:** A1 Splash (sin sesión) -> A2 -> A3 -> A4 (Continuar con Google; Saltar lleva a A4, nunca se salta el login) -> B1 (12 preguntas) -> B2 (modos; si activa Pareja, B3) -> B4 (consentimiento) -> B5 -> D1 o C1.
2. **Sesión existente:** A1 -> C1 directo (sin onboarding ni Google).
3. **Navegación principal:** NavBar Inicio (C1), Parche (D1), Conocer (F1), Chats, Perfil (C3).
4. **Cargar el parche:** D4 cargando -> D1 con conexiones, D3 vacío o D5 error -> Reintentar -> D4.
5. **Explorar una conexión:** D1 -> tocar nodo -> D2 -> "Simular de nuevo" (F4) o "Chatear" (F9).
6. **Agregar amigos:** D1 (person_add) o D3 -> C5 (mi código / escanear / ingresar código).
7. **Match al azar:** C1 "Buscar" o D3 -> F1 búsqueda -> F2 (Escenario o Simulación) -> F3 chat con escenario, o F4 simulación -> F7 (gratis) / F8 (ParcheU+) -> Conectar -> F9. Cancelar búsqueda no descuenta cupo.
8. **Simulación con alguien del parche:** D2 / C1 Sugerencia -> F4 (3 escenarios, segundo plano, aviso) -> F7/F8 -> F9. Error: F5 -> reintentar.
9. **Resultado y conexión mutua:** F7/F8 -> "Conectar" -> espera a la otra persona -> F9. "Ahora no" no notifica.
10. **Presentar a dos amigos:** E1 (modo Amistad o Estudio, dos amigos) -> E2 -> "Enviar propuesta a los dos" -> E3 en el celular de cada uno -> ambos "Me interesa" -> F9.
11. **Amigos de amigos (premium):** D1 banner -> gratis D7 -> G1; ParcheU+ D6 -> "Pedir presentación" o "Simular" (F4).
12. **Comunidades y evolución (premium):** D1 -> D8 -> D1 filtrado; D1 timeline -> D9 -> D1 con snapshot.
13. **Compra:** límite G2 -> G1 (anual por defecto) -> Play Billing -> G3 -> D6 o C1. Restaurar compras desde G1 y G4. Gestión: Perfil/Ajustes -> G4.
14. **Entrenar al gemelo:** C3 -> C4 (aceptar/rechazar memorias, calificar respuesta) -> Listo -> C3.
15. **Notificaciones:** C1 campana -> C2 -> resultado (F7/F8), presentación (E3), bloqueado (G1), aviso del gemelo (C4).
16. **Reportar/bloquear:** F9 menú -> H1 -> H2 -> envío -> confirmación (sin diseñar). F3 tiene bandera directa a H1.
17. **Privacidad:** C3 engranaje -> H3 (modos, visibilidad nombre / "Alguien" / oculto, aprender de chats, borrar gemelo, exportar datos). Activar Pareja pasa por B3.
18. **Exportar datos (2026-10-23):** H3 "Exportar mis datos" -> H4 (categorías + formato JSON / PDF) -> "Exportar mis datos" -> enlace de descarga al correo (7 días); confirmación sin diseñar.
19. **Borrar cuenta (2026-10-23):** H3 "Borrar mi gemelo y mis datos" -> H5 -> (opcional "Exportar" -> H4) -> escribir BORRAR -> "Borrar mi cuenta" -> cierra sesión -> A1. Nota: 14 días para arrepentirse iniciando sesión (pantalla de reactivación sin diseñar).

## 6. Diferencias con los requisitos

Basado en las notas de cada inventario parcial (el texto íntegro de los requisitos no estaba entre los insumos; los números entre paréntesis son los que citan los parciales).

### 6.1 Lo que los mockups agregan

- Login con Google fusionado dentro del onboarding paso 3 (A4); no hay pantalla de login independiente.
- Respuesta "Depende" y gestos de swipe en las 12 preguntas (B1); gamificación: Nivel 6, XP 340/500, "Nivel 1 · 12 respuestas" (B5, C3), métrica "Qué tanto te conoce" mensual.
- Entrena a tu gemelo (C4): confirmar memorias extraídas de chats y calificar respuestas simuladas. Lista "Ver 24" sin diseñar.
- Sugerencias para ti en Inicio (C1) con "amigos en común" (roza la función premium de amigos de amigos).
- Notificaciones agrupadas (Hoy / Esta semana) y "Marcar leídas" (C2).
- Agregar amigos por QR/código con formato XXXX-0000 (C5).
- Banner "Amigos de amigos y comunidades" con candado; chips "Afinidad 70 %+" y "Comunidad" (estáticos) en D1.
- "Pedir presentación" a un contacto anónimo de 2.º grado (D6): flujo inverso no especificado.
- Perfil con carrera y universidad ("Ing. Industrial · UIS") y datos del match al azar ("Alguien de la UIS · 20-22 años · Bucaramanga", intereses).
- Comunidades con cadencia semanal y hint predictivo; Evolución con snapshots mensuales y "Momentos" (requieren historial en backend).
- Elegir Escenario o Simulación al terminar el match (F2); escenario con temporizador y presupuesto (F3); simulación de 3 escenarios en segundo plano con aviso push (F4); "Ver la conversación de los gemelos" (F8).
- Regla "no se descuenta del cupo" al cancelar búsqueda o fallar simulación (F1, F5).
- Botón compartir en resultados (F7, F8).
- Banner de consentimiento dentro del chat (F9); "Silenciar" en el sheet de bloqueo (H1).
- Pantallas de suscripción: Historial de pagos, Cambiar a mensual (solo anual a mensual) (G4); tabla comparativa con "Reportar y bloquear: gratis siempre"; badge "2 meses gratis".
- "Borrar mi gemelo y mis datos" (H3), útil para Google Play; desde 2026-10-23 tiene pantalla propia (H5) con confirmación escrita 'BORRAR', lista de lo que se borra, aviso de ParcheU+ y periodo de arrepentimiento de 14 días.
- Exportar mis datos (H4, 2026-10-23): selección por categorías (perfil y modos, gemelo, parche, simulaciones, chats), formato JSON o "PDF legible" y envío de un enlace al correo que dura 7 días.
- Tokens nuevos no pedidos: degradado ParcheU+, tints de texto por modo, escala de superficies s1/s2, tema claro completo, fuentes Sora/Inter.
- Etiquetas de afinidad "por descubrir / media / alta".

### 6.2 Lo que los mockups quitan u omiten

- Pantalla de lista de Chats (el NavBar apunta a "Chats" pero no hay mockup).
- Badge de no leídos en NavBar y entrada para FCM.
- Punto de entrada de C5 (Agregar amigos), de D6/D8 para usuarios ParcheU+, y de G4 (Suscripción) desde Perfil.
- Estados vacío, cargando y error en casi todas las pantallas (solo D3-D5, F5, F6 los tienen).
- Bloqueado de Comunidades y Evolución (solo existe D7 para amigos de amigos); D1 para ParcheU+; contadores a 0 y estado ParcheU+ en Inicio.
- Informe completo "1 vez al mes" gratis: F7 solo muestra el estado ya consumido; falta el estado disponible. Límites de match diario e informe mensual sin hoja propia (G2 cubre solo simulaciones).
- Confirmaciones de bloqueo y de reporte enviado, confirmación de cancelar suscripción, resultado de restaurar compras, error de compra. Borrar datos ya tiene pantalla (H5), pero faltan sus estados borrando/error y la confirmación de exportación enviada (H4).
- H3 sigue sin 'Personas bloqueadas', 'Cerrar sesión' ni enlaces a Términos y Política (los pide E8.2).
- Botón "Ahora no" en B4 (la anotación lo menciona pero la pantalla solo tiene "Acepto"); error de menor de 18 en B3; validación de 0 modos en B2.
- Cámara de "Escanear"; acción tras "Pedir presentación"; sheet para vínculos "simularon" y "pendiente".
- Pareja ya excluido en E1 por nota, no por segmento deshabilitado (consistente con el requisito 9).

### 6.3 Lo que los mockups contradicen o dejan ambiguo

- **Modos por defecto:** B2 muestra los tres modos activados; en producción deberían iniciar apagados, sobre todo Pareja (requiere +18).
- **Verificación +18:** el mockup solo muestra campo de fecha y checkbox; los requisitos piden verificación en servidor. H3 activa Pareja sin gate; debe pasar por B3.
- **Bloqueo premium:** los requisitos piden "borroso con candado"; D1 e I2 usan un banner con candado sin blur, y C2 un ícono de candado simple. Solo D7 y F7 siguen la regla.
- **Cuota:** C1 muestra 3 de 5, G2 (hoja) dice 5 de 5 sobre el mismo fondo de Inicio: el fondo debe reflejar 5 de 5. E1 gasta cupo del que presenta.
- **Informe "Por qué funcionaría" (E2)** aparece completo y gratis, aunque el informe completo es 1/mes en gratis.
- **Anonimato del match:** F1-F3 usan "Alguien de la UIS" y F4-F9 usan "Laura Pinzón" con modo Estudio; no se define cuándo se revela el nombre.
- **Pareja en grafos de terceros:** D1 incluye un vínculo Pareja propio (correcto); la regla de no mostrarlo a otros se cumple en D6, pero PU Grafo no la aplica por sí solo.
- **Stat "9 conexiones" (D1)** cuenta 9 nodos de los cuales 5 son conectados; definir qué cuenta como conexión.
- **Compartir resultados** choca con la privacidad de Pareja y no estaba pedido.
- **Visibilidad por defecto** en H3: "Aparecer como «Alguien»" (vis b); confirmar que es el default de producto.
- **NavBar:** D6 y D9 (hijas con back) no la muestran; D7 sí. Inconsistente; recomendado sin NavBar en pantallas con back.
- **Nombres de sección:** el mockup llama "Conocer" a la pestaña de match al azar; no está fijado en requisitos. "Parche" va en la 2.ª posición, no al centro.
- **Contraste:** textos de 9.5-10.5 px en el grafo y chips con texto oscuro sobre #3A86FF por verificar.
- **Promesa operativa:** "Lo revisa una persona del equipo en menos de 24 h" (H2) debe ser sostenible con un equipo de 3.
- **Cuenta de autenticación:** no hay pantalla de login tras cerrar sesión; A4 debe ser alcanzable luego de un logout. La sesión de Firebase evita repetir el paso si ya hay sesión (A1 va directo a C1).
- **Typos de copy:** "Para qué quieres tu parche?" y "Qué más, Mari?" sin signo de apertura (el texto de otras secciones sí lo trae).
- **Exportación (H4) frente a E8.3:** el mockup envía un **enlace al correo** (7 días) con formato JSON o **PDF**; E8.3 especifica `GET /v1/me/export` en JSON descargado en el teléfono con `ACTION_CREATE_DOCUMENT` o la hoja de compartir, y así quedó implementado (#59). El PDF y el envío por correo no están en el alcance; tampoco la selección por categorías (la API exporta todo). La categoría "Chats" desmarcada por defecto choca con E8.3, que incluye los mensajes propios.
- **Borrado (H5) frente a E8.3:** el mockup promete "Tienes 14 días para arrepentirte iniciando sesión"; E8.3 hace borrado lógico inmediato y purga con `purge-user` sin periodo de gracia. La confirmación del mockup es escribir 'BORRAR'; la implementación de #59 usa la casilla 'Entiendo que no se puede deshacer'. Decidir cuál manda antes de cambiar el código.
- **Anotación de H4:** dice "Borrar mi cuenta → 24b" pero la pantalla no tiene ese botón.
- **Resolución (2026-10-23):** la diferencia de H4/H5 con E8.3 se resolvió a favor del código (#59 y #62). El mockup H4/H5 queda como implementación futura post-1.0; ver la ficha [E8.3](backlog.md#e83--borrado-y-exportación-de-cuenta).
- **Fechas y precios hardcodeados** ("lunes 26 de octubre", "21 de octubre de 2027", $45.000, $5.000, $3.750/mes; en H4/H5: correo, "Nivel 6 · 24 aprendizajes", "9 conexiones", "14 simulaciones") deben venir del backend y de Play Billing.

### 6.4 Pantallas referenciadas en los mockups que no existen

Lista de chats, lista completa de aprendizajes (Ver 24), Ver todas las simulaciones, cámara de escaneo, historial de pagos, confirmaciones (bloqueo, reporte enviado, cancelar suscripción, exportación enviada; borrar datos ya existe como H5), reactivación de cuenta durante los 14 días de arrepentimiento (H5), estado "esperando a la otra persona" tras Conectar o Me interesa, hoja de límite para match diario e informe mensual, variante mensual de G3 y G4.
