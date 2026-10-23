package me.santiagoreina.parcheu.api.twin

import me.santiagoreina.parcheu.contract.TraitKey
import me.santiagoreina.parcheu.contract.TwinAnswer
import java.time.Instant
import java.util.UUID

/** Repositorio en memoria para tests y `ENV=local` sin base. Las preguntas son las semillas de V2. */
class InMemoryTwinRepository(
    private val questions: List<WeightedQuestion> = SeedQuestions.all,
) : TwinRepository {
    private class Row(
        var traits: List<Double>,
        var summary: String? = null,
        var ready: Boolean = false,
        var version: Int = 1,
        var xp: Int = 0,
    )

    private val twins = HashMap<UUID, Row>()
    private val answers = HashMap<UUID, Map<Int, TwinAnswer>>()
    private val accuracy = HashMap<UUID, MutableList<AccuracyPoint>>()

    /** `twin_versions` por usuario: versión -> persona (para comprobar que no se duplica). */
    val versions = HashMap<UUID, MutableMap<Int, String>>()

    @Synchronized override fun questions() = questions.sortedBy { it.ord }

    @Synchronized
    override fun saveAnswers(userId: UUID, answers: Map<Int, TwinAnswer>, traits: List<Double>, now: Instant): SavedAnswers {
        val first = this.answers[userId].isNullOrEmpty()
        this.answers[userId] = answers.toMap()
        val row = twins.getOrPut(userId) { Row(traits) }
        row.traits = traits
        if (first) {
            row.xp += answers.size * XP_PER_ANSWER
        } else {
            row.version += 1
            row.ready = false
        }
        return SavedAnswers(stateOf(userId)!!, row.version)
    }

    @Synchronized override fun state(userId: UUID) = stateOf(userId)

    private fun stateOf(userId: UUID): TwinState? = twins[userId]?.let {
        TwinState(it.traits, it.ready, it.version, levelFor(it.xp), it.xp, accuracy[userId]?.maxByOrNull { p -> p.week }?.accuracy)
    }

    @Synchronized override fun accuracyPoints(userId: UUID) = accuracy[userId].orEmpty().sortedBy { it.week }

    @Synchronized
    override fun personaInput(userId: UUID): PersonaInput? {
        val row = twins[userId] ?: return null
        val given = answers[userId] ?: return null
        val byId = questions.associateBy { it.id }
        return PersonaInput(
            version = row.version,
            personaReady = row.ready,
            traits = row.traits,
            answers = given.entries.sortedBy { byId.getValue(it.key).ord }.map { AnsweredQuestion(byId.getValue(it.key), it.value) },
        )
    }

    @Synchronized
    override fun savePersona(userId: UUID, version: Int, summary: String, now: Instant): Boolean {
        val row = twins[userId] ?: return false
        if (row.version != version || row.ready) return false
        row.summary = summary
        row.ready = true
        versions.getOrPut(userId) { HashMap() }[version] = summary
        return true
    }

    @Synchronized
    override fun addAccuracyPoint(userId: UUID, point: AccuracyPoint) {
        accuracy.getOrPut(userId) { mutableListOf() }.apply { removeAll { it.week == point.week }; add(point) }
    }

    /** Persona guardada (para asserts). */
    @Synchronized fun personaOf(userId: UUID): String? = twins[userId]?.summary
}

/** Las 12 preguntas de `V2__semillas.sql`, para los repositorios en memoria. */
object SeedQuestions {
    private fun q(id: Int, text: String, a: String, b: String, vararg w: Pair<TraitKey, Double>) =
        WeightedQuestion(id, id, text, a, b, w.toMap())

    val all: List<WeightedQuestion> = listOf(
        q(1, "Es viernes en la noche y no tienes planes. ¿Qué haces?", "Le escribo al parche para salir", "Me quedo en casa recargando pilas",
            TraitKey.SOCIAL_ENERGY to 1.0, TraitKey.OPENNESS to 0.3),
        q(2, "Se viene un parcial en dos semanas.", "Armo un horario y estudio por partes", "Le meto todo la última semana",
            TraitKey.PLANNING to 1.0, TraitKey.ACADEMIC_AMBITION to 0.5),
        q(3, "Alguien del grupo de trabajo no está haciendo su parte.", "Se lo digo de frente y buscamos solución", "Lo dejo pasar para no armar problema",
            TraitKey.CONFLICT_HANDLING to 1.0),
        q(4, "En un parche donde no conoces a casi nadie...", "Rompo el hielo con un chiste", "Observo primero y hablo después",
            TraitKey.HUMOR to 1.0, TraitKey.SOCIAL_ENERGY to 0.5),
        q(5, "Te ofrecen entrar a un semillero de investigación que exige tiempo extra.", "De una, me sirve para la carrera", "Prefiero tener tiempo libre",
            TraitKey.ACADEMIC_AMBITION to 1.0),
        q(6, "Un amigo te invita a probar comida que nunca has comido.", "Me le mido a lo que sea", "Mejor pido lo de siempre",
            TraitKey.OPENNESS to 1.0),
        q(7, "Para un paseo de fin de semana con amigos...", "Llevo itinerario, presupuesto y reservas", "Vamos viendo sobre la marcha",
            TraitKey.PLANNING to 1.0, TraitKey.OPENNESS to -0.3),
        q(8, "Tienes un desacuerdo fuerte con alguien cercano.", "Prefiero hablarlo ya, aunque sea incómodo", "Necesito tiempo antes de hablarlo",
            TraitKey.CONFLICT_HANDLING to 1.0, TraitKey.SOCIAL_ENERGY to 0.2),
        q(9, "En el chat del grupo, tú eres más de...", "Mandar memes y stickers", "Responder corto y al punto",
            TraitKey.HUMOR to 1.0),
        q(10, "Sacaste 3,5 en una materia que pasaste.", "Me molesta, sé que podía sacar más", "Pasé, eso es lo importante",
            TraitKey.ACADEMIC_AMBITION to 1.0, TraitKey.PLANNING to 0.3),
        q(11, "Te proponen un plan improvisado a las 10 p. m.", "Me apunto, para eso es la vida", "Ya tenía mi noche organizada",
            TraitKey.OPENNESS to 1.0, TraitKey.SOCIAL_ENERGY to 0.5, TraitKey.PLANNING to -0.3),
        q(12, "Cuando algo te sale mal frente a otros...", "Me río de mí y sigo", "Me da pena y prefiero que no lo comenten",
            TraitKey.HUMOR to 1.0, TraitKey.CONFLICT_HANDLING to 0.3),
    )
}
