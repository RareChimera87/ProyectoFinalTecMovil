package me.santiagoreina.parcheu.api.graph

import me.santiagoreina.parcheu.api.graph.fof.FofService
import me.santiagoreina.parcheu.api.home.HomeSuggestions
import me.santiagoreina.parcheu.api.social.introductions.IntroductionPeople
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.Visibility
import java.util.UUID
import kotlin.reflect.KClass

/** Contexto en el que un observador ve a otra persona (arquitectura.md §6.1). */
enum class ViewContext {
    /** Su propio ego-grafo: personas con las que tiene una arista directa. */
    EGO,

    /** Segundo grado: fof, comunidades de otros, presentaciones, sugerencias, nodos de terceros. */
    THIRD_PARTY,
}

/** Cómo se presenta una persona a un observador: con nombre o enmascarada con su `anonId`. */
data class PersonView(
    /** `users.id` si hay conexión mutua; si no, el `anonId` del observador. */
    val id: String,
    val label: String,
    val initials: String,
    val photoUrl: String?,
    val anonymous: Boolean,
    val isDemo: Boolean,
)

/**
 * Única clase que decide qué ve un observador sobre otra persona (ADR-0012). Reglas:
 * 1. nunca aristas de Pareja que no involucren al observador;
 * 2. nunca usuarios HIDDEN en contextos de segundo grado;
 * 3. sin conexión mutua (o ANONYMOUS en segundo grado) la persona se enmascara con `anonId`;
 * 4. nunca bloqueados (en cualquier sentido) ni borrados.
 *
 * Los mismos filtros se aplican también en SQL; esta clase es la segunda barrera.
 */
class GraphVisibilityPolicy(private val anonIds: AnonIdSigner) {

    /** Regla 4 (y 2 en segundo grado): si [viewer] puede ver a [subject] en [context]. */
    fun canSee(viewer: UUID, subject: PersonRow, context: ViewContext, blocked: Set<UUID>): Boolean = when {
        subject.id == viewer -> subject.deletedAt == null
        subject.deletedAt != null -> false
        subject.id in blocked -> false
        context == ViewContext.THIRD_PARTY && subject.visibility == Visibility.HIDDEN -> false
        else -> true
    }

    /** Regla 1: una arista de Pareja solo es visible para sus dos extremos. */
    fun canSeeEdge(viewer: UUID, endpointA: UUID, endpointB: UUID, mode: Mode): Boolean =
        mode != Mode.PAREJA || viewer == endpointA || viewer == endpointB

    /**
     * Regla 3. En el ego-grafo una conexión directa mutua ([connected]) siempre ve el nombre; sin
     * ella la persona sale como "Alguien" (con su universidad si la tiene). En segundo grado solo
     * se muestra el nombre si su visibilidad es NAME o si hay conexión mutua.
     */
    fun present(viewer: UUID, subject: PersonRow, connected: Boolean, context: ViewContext): PersonView {
        val named = connected || (context == ViewContext.THIRD_PARTY && subject.visibility == Visibility.NAME)
        val name = subject.displayName?.trim()?.takeIf { it.isNotEmpty() }
        return if (named && name != null) {
            PersonView(
                id = if (connected) subject.id.toString() else anonIds.anonId(viewer, subject.id),
                label = name,
                initials = initialsOf(name),
                photoUrl = subject.photoUrl,
                anonymous = false,
                isDemo = subject.isDemo,
            )
        } else {
            PersonView(
                id = anonIds.anonId(viewer, subject.id),
                label = anonymousLabel(subject.universityShort),
                initials = ANON_INITIALS,
                photoUrl = null,
                anonymous = true,
                isDemo = subject.isDemo,
            )
        }
    }

    companion object {
        const val ANON_LABEL = "Alguien"
        const val ANON_INITIALS = "A"

        /** "Alguien de la UIS"; sin universidad (o con 'Otra') queda en "Alguien". */
        fun anonymousLabel(universityShort: String?): String =
            if (universityShort.isNullOrBlank() || universityShort == "Otra") ANON_LABEL else "$ANON_LABEL de la $universityShort"

        /** Iniciales de hasta dos palabras ("Laura Pérez" -> "LP"). */
        fun initialsOf(name: String): String =
            name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { ANON_INITIALS }
    }
}

/**
 * Vista que muestra a un observador datos de terceros (segundo grado). Toda implementación debe
 * pasar por [GraphVisibilityPolicy] y registrarse en [ThirdPartyViews]; la suite de invariantes de
 * privacidad (E3.5) recorre ese registro.
 */
interface ThirdPartyView {
    /** Nombre estable de la vista (p. ej. "fof", "communities"). */
    val viewName: String
}

/**
 * Registro de las vistas de terceros. El ego-grafo de E3.1 no lo es (solo muestra aristas
 * directas del observador); fof (E6.3), comunidades (E6.4), presentaciones (E6.2) y sugerencias
 * de inicio (E4.7) agregan aquí su clase en su feature.
 *
 * Es declarativo (clases, no instancias) para que el test de arquitectura de E3.5 lo compare con
 * las clases que implementan [ThirdPartyView] sin arrancar la aplicación: si una clase no está en
 * [registered], o está pero no tiene sonda en la suite de invariantes, el build falla.
 */
object ThirdPartyViews {
    val registered: Set<KClass<out ThirdPartyView>> = setOf(
        // Sugerencias de Inicio (E4.7): el conteo de amigos en común lee aristas del candidato.
        HomeSuggestions::class,
        // Presentaciones (E6.2): la otra persona presentada (E3) y los amigos de amigos de 'Pedir presentación'.
        IntroductionPeople::class,
        // Amigos de amigos (E6.3): candidatos a dos saltos y las aristas que los conectan.
        FofService::class,
    )

    fun names(): Set<String> = registered.mapNotNull { it.simpleName }.toSet()
}
