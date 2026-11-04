package me.santiagoreina.parcheu.core.observability

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Claves de contexto que acompañan cada cierre inesperado (arquitectura.md §11, E10.5).
 * Nunca llevan datos personales: solo el nombre de la pantalla y el flavor del build.
 */
object CrashKeys {
    const val ROUTE = "route"
    const val FLAVOR = "flavor"
    const val ROUTE_UNKNOWN = "unknown"

    /**
     * Convierte la ruta tipada de Navigation (p. ej. `me.santiagoreina.parcheu.navigation.Chat/{chatId}`
     * o con argumentos ya resueltos) en un nombre corto y estable (`Chat`). Se descarta todo lo que
     * sigue al primer `/` o `?`, porque ahí viajan los argumentos (ids de chat, códigos de invitación).
     */
    fun routeName(rawRoute: String?): String {
        val head = rawRoute?.substringBefore('/')?.substringBefore('?')?.trim().orEmpty()
        val name = head.substringAfterLast('.')
        return name.ifEmpty { ROUTE_UNKNOWN }
    }
}

/** Puerta de salida hacia el reporte de fallos; la app no depende de Firebase directamente. */
interface CrashReporter {
    fun setKey(key: String, value: String)

    /** Registra un error no fatal (visible en Crashlytics sin cerrar la app). */
    fun recordNonFatal(error: Throwable)
}

/** Sin efecto: pruebas y arranques donde Firebase no está inicializado. */
object NoopCrashReporter : CrashReporter {
    override fun setKey(key: String, value: String) = Unit

    override fun recordNonFatal(error: Throwable) = Unit
}

/** Implementación sobre Crashlytics. Cada llamada tolera que Firebase aún no esté listo. */
class CrashlyticsReporter(
    private val crashlytics: () -> FirebaseCrashlytics = { FirebaseCrashlytics.getInstance() }
) : CrashReporter {
    override fun setKey(key: String, value: String) {
        runCatching { crashlytics().setCustomKey(key, value) }
    }

    override fun recordNonFatal(error: Throwable) {
        runCatching { crashlytics().recordException(error) }
    }
}

/** Reporter de proceso; lo fija [install] al arrancar la Application. */
object CrashReporting {
    @Volatile
    var reporter: CrashReporter = NoopCrashReporter
        private set

    /**
     * Activa Crashlytics y fija la clave `flavor`. En builds debug se desactiva la recolección para
     * no ensuciar el panel con errores de desarrollo; los dev release y los prod sí reportan.
     */
    fun install(flavor: String, isDebug: Boolean) {
        val real = CrashlyticsReporter()
        runCatching { FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!isDebug) }
        reporter = real
        real.setKey(CrashKeys.FLAVOR, flavor)
    }

    fun setRoute(rawRoute: String?) {
        reporter.setKey(CrashKeys.ROUTE, CrashKeys.routeName(rawRoute))
    }

    /** Solo para pruebas. */
    fun overrideForTest(replacement: CrashReporter) {
        reporter = replacement
    }
}
