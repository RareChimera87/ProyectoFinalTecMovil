# ADR-0005: Koin y DataStore, sin KSP ni kapt

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.7

## Contexto
El proyecto base usa AGP 9.4.1 con Kotlin integrado (sin plugin `kotlin-android`), Kotlin 2.2.10, Gradle 9.6 y SDK 37. Hilt y Room dependen de KSP; la compatibilidad de esas herramientas con el Kotlin integrado de AGP 9 es reciente y una falla tumbaría a la vez la DI y la persistencia.

## Decisión
- DI con **Koin 4.1** (sin generación de código).
- Persistencia local con **DataStore** (sesión, preferencias, último grafo) y la caché offline de Firestore para chats. Sin Room.
- Spike el día 1 de E0.7: confirmar que el plugin de compilador `org.jetbrains.kotlin.plugin.serialization` 2.2.10 funciona con el Kotlin integrado. Si falla, plan B: declarar el plugin con `builtInKotlin=false` en `gradle.properties` (verificar el nombre exacto de la propiedad) o usar `kotlinx-serialization` solo en `contract` y Moshi/Gson en Android.

## Alternativas consideradas
- **Hilt + KSP:** compatibilidad con AGP 9 por confirmar; versiones de Hilt anteriores a la que soporta AGP 9 fallan.
- **Room:** necesita KSP; la app no requiere consultas locales complejas.

## Consecuencias
- Menos riesgo de toolchain; los errores de DI aparecen en tiempo de ejecución (se cubren con un test que verifica los módulos Koin: `verify()`).
- Sin base local relacional: la app depende de la red salvo el chat (Firestore) y el último grafo (DataStore).

## Resultado del spike
- **Fecha:** 22 oct 2026 (E0.7, antes del límite del 25 oct).
- **Versión:** plugin `org.jetbrains.kotlin.plugin.serialization` 2.2.10 (alias `kotlin-serialization` en `libs.versions.toml`) con AGP 9.4.1 y su Kotlin integrado, Gradle 9.6.0, `kotlinx-serialization-json` 1.9.0.
- **Resultado: funciona sin plan B.** El plugin se aplica en `:app` junto a `com.android.application` y `org.jetbrains.kotlin.plugin.compose`, sin `kotlin-android` ni propiedades extra en `gradle.properties`. Lo prueban las 36 rutas `@Serializable` de Navigation (test parametrizado `AllRoutesNavigationTest`), el mapeo de `ApiError` del contrato (`ApiErrorMapperTest`) y `assembleProdRelease` con R8 (reglas en `app/src/main/keepRules/rules.keep`).
- **Hallazgo relacionado:** el compilador de Kotlin 2.2 solo lee metadatos hasta 2.3. Algunas versiones recientes ya vienen compiladas con Kotlin 2.4 (googleid 1.2.x, Coil 3.5+, Firebase BoM 35 por firebase-auth 25) y rompen la compilación; se fijaron versiones anteriores (arquitectura.md §13).
