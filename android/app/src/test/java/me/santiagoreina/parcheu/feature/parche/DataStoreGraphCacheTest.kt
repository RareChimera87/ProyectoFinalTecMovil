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
        assertNull(cache.load())

        cache.save(ParcheSamples.content.copy(plus = true))
        assertEquals(ParcheSamples.content.copy(plus = true), cache.load())

        cache.clear()
        assertNull(cache.load())
    }
}
