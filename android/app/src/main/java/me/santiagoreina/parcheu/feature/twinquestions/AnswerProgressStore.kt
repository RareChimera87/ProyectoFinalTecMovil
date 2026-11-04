package me.santiagoreina.parcheu.feature.twinquestions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import me.santiagoreina.parcheu.contract.TwinAnswer

/**
 * Guardado parcial de las respuestas de B1 para retomar tras cerrar la app. Las respuestas se guardan
 * por id de pregunta; se borran al enviarlas con éxito.
 */
interface AnswerProgressStore {
    suspend fun load(): Map<Int, TwinAnswer>

    suspend fun save(answers: Map<Int, TwinAnswer>)

    suspend fun clear()
}

/** Implementación sobre DataStore Preferences (ADR-0005). Formato: `1:A,2:DEPENDS`. */
class DataStoreAnswerProgressStore(private val dataStore: DataStore<Preferences>) :
    AnswerProgressStore {
    override suspend fun load(): Map<Int, TwinAnswer> = decode(dataStore.data.first()[KEY])

    override suspend fun save(answers: Map<Int, TwinAnswer>) {
        dataStore.edit { it[KEY] = encode(answers) }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    internal companion object {
        private val KEY = stringPreferencesKey("twin_questions_progress")

        fun encode(answers: Map<Int, TwinAnswer>): String =
            answers.entries.joinToString(",") { "${it.key}:${it.value.name}" }

        /** Ignora entradas corruptas en vez de fallar: perder una respuesta es mejor que bloquear B1. */
        fun decode(raw: String?): Map<Int, TwinAnswer> {
            if (raw.isNullOrBlank()) return emptyMap()
            val result = LinkedHashMap<Int, TwinAnswer>()
            for (part in raw.split(',')) {
                val (id, name) = part.split(':', limit = 2).takeIf { it.size == 2 } ?: continue
                val questionId = id.toIntOrNull() ?: continue
                val answer = TwinAnswer.entries.firstOrNull { it.name == name } ?: continue
                result[questionId] = answer
            }
            return result
        }
    }
}
