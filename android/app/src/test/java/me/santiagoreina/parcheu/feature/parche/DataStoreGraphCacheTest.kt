package me.santiagoreina.parcheu.feature.parche

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ContractJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** El último grafo sobrevive en DataStore y se borra al cerrar sesión. */
class DataStoreGraphCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun cache() = DataStoreGraphCache(
        PreferenceDataStoreFactory.create {
            folder.newFile("parche.preferences_pb").also { it.delete() }
        },
        ContractJson
    )

    @Test
    fun saveLoadAndClear() = runTest {
        val cache = cache()
        assertNull(cache.load("a"))

        cache.save(ParcheSamples.content.copy(plus = true), "a")
        assertEquals(ParcheSamples.content.copy(plus = true), cache.load("a"))

        cache.clear()
        assertNull(cache.load("a"))
    }

    @Test
    fun graphOfAnotherUserIsNeverReturnedAndIsDiscarded() = runTest {
        val cache = cache()
        cache.save(ParcheSamples.content, "a")

        assertNull(cache.load("b"))
        // Ya se descartó: ni siquiera su dueña lo recupera.
        assertNull(cache.load("a"))
    }
}
