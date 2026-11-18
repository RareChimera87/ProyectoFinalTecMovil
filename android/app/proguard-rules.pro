# Reglas de R8 del módulo app (buildType release, enlazadas en app/build.gradle.kts).
#
# Contexto (fix/crash-r8-release, 0.2.1): el APK de prod minificado se cerraba al arrancar con
#   IllegalArgumentException: Cannot find class with name "me.santiagoreina.parcheu.contract.Mode?"
# Navigation Compose con rutas tipadas resuelve por reflexión (Class.forName sobre el serialName del
# descriptor) la clase de cada argumento enum al construir el grafo, y R8 había renombrado el enum del
# contrato (mapping.txt: me.santiagoreina.parcheu.contract.Mode -> dt2). El contrato es Kotlin JVM puro y no
# puede usar androidx.annotation.Keep, así que las reglas viven aquí.
#
# Criterio: conservador a propósito. Se conserva de más el código propio (contrato, rutas, DTOs y enums)
# para no arriesgar otro cierre más adelante en el flujo; las bibliotecas siguen minificándose con sus
# propias reglas de consumidor. RouteKeepRulesTest comprueba que cada argumento no primitivo de una ruta
# queda cubierto por este archivo.

# --- Navigation Compose: argumentos de ruta -------------------------------------------------------------

# Contrato completo (DTOs, enums y serializers propios como IsoDateSerializer) con su nombre y miembros.
# Cubre Mode (MatchQueue.mode) y cualquier enum o tipo del contrato que se use mañana como argumento de
# ruta: Navigation hace Class.forName("<paquete>.<Clase>") y, en enums, busca la constante por nombre
# (enumConstants + name). Conservar la clase también impide que R8 "desempaquete" el enum a un int.
-keep class me.santiagoreina.parcheu.contract.** { *; }
-keep enum me.santiagoreina.parcheu.contract.** { *; }

# Enums propios de la app: mismo motivo si alguno pasa a ser argumento de ruta, y Enum.valueOf/entries
# sobre nombres que llegan de fuera (Firestore, DataStore).
-keep enum me.santiagoreina.parcheu.** { *; }

# Rutas tipadas (AppRoute y sus subclases @Serializable): nombre, miembros, Companion/INSTANCE y serializer().
# hasRoute(KClass) y el catálogo de rutas obtienen el serializer de la clase por reflexión
# (KClass.serializer()), que busca el campo Companion o INSTANCE por nombre.
-keep class me.santiagoreina.parcheu.navigation.AppRoute { *; }
-keep class * implements me.santiagoreina.parcheu.navigation.AppRoute { *; }
-keep class me.santiagoreina.parcheu.navigation.** { *; }

# --- kotlinx.serialization -------------------------------------------------------------------------------

# Anotaciones e información de genéricos y clases internas: el convertidor de Retrofit pide el serializer
# por el tipo java.lang.reflect.Type de cada método (List<Dto>, Page<Dto>), y kotlinx.serialization lee
# anotaciones (@Serializable, @SerialName) en tiempo de ejecución en la búsqueda por reflexión.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exceptions

# Toda clase @Serializable propia (contrato, rutas, ParcheContent de DataStore…): clase, miembros y el
# serializer generado ($$serializer). Más amplio que las reglas que trae kotlinx-serialization-core.
-keep @kotlinx.serialization.Serializable class me.santiagoreina.parcheu.** { *; }
-keep class me.santiagoreina.parcheu.**$$serializer { *; }

# Reglas estándar de kotlinx.serialization (README del plugin) para cualquier @Serializable: el campo
# Companion, el método serializer() del companion y, en objects, INSTANCE y serializer().
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# --- Retrofit --------------------------------------------------------------------------------------------

# Las interfaces *Api se crean con Retrofit.create(XApi::class.java) (proxy dinámico) y sus métodos se leen
# por reflexión (anotaciones HTTP, firma genérica con Continuation<T> en las suspend). Retrofit 3 trae reglas
# para el modo completo de R8; esto las refuerza sin depender de ellas para las interfaces propias.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
