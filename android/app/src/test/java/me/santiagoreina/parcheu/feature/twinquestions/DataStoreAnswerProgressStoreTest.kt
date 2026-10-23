package me.santiagoreina.parcheu.feature.twinquestions

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.santiagoreina.parcheu.contract.TwinAnswer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreAnswerProgressStoreTest {
    private val dir = File.createTempFile("twinq", "").apply {
        delete()
        mkdirs()
    }
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newStore(): DataStoreAnswerProgressStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "p.preferences_pb") }
        return DataStoreAnswerProgressStore(ds)
    }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    @Test
    fun savedAnswersSurviveReopeningTheStore() = runBlocking {
        newStore().save(mapOf(1 to TwinAnswer.A, 2 to TwinAnswer.DEPENDS, 3 to TwinAnswer.B))
        // Un DataStore nuevo sobre el mismo archivo simula cerrar y reabrir la app.
        scopes.forEach { it.cancel() }
        assertEquals(
            mapOf(1 to TwinAnswer.A, 2 to TwinAnswer.DEPENDS, 3 to TwinAnswer.B),
            newStore().load()
        )
    }

    @Test
    fun clearRemovesProgress() = runBlocking {
        val store = newStore()
        store.save(mapOf(1 to TwinAnswer.A))
        store.clear()
        assertEquals(emptyMap<Int, TwinAnswer>(), store.load())
    }

    @Test
    fun corruptEntriesAreIgnored() {
        assertEquals(
            mapOf(2 to TwinAnswer.B),
            DataStoreAnswerProgressStore.decode("x:A,1:Z,2:B,3,:A")
        )
        assertEquals(emptyMap<Int, TwinAnswer>(), DataStoreAnswerProgressStore.decode(null))
    }
}
