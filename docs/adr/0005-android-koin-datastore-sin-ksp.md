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
_Completar en E0.7, **a más tardar el 25 oct 2026**: fecha, versión del plugin, resultado y, si falla, plan B aplicado._
