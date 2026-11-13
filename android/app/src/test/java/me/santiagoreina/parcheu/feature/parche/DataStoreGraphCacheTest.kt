package me.santiagoreina.parcheu.feature.parche

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private fun store() = PreferenceDataStoreFactory.create {
        folder.newFile("parche.preferences_pb").also { it.delete() }
    }

    private fun cache() = DataStoreGraphCache(store(), ContractJson)

    /** Un grafo guardado por la app anterior (sin `affinityMeasured`) se sigue leyendo con su afinidad. */
    @Test
    fun graphSavedWithThePreviousFormatIsStillRead() = runTest {
        val store = store()
        val previous =
            """
            {"graph":{
            "me":{"id":"me","label":"Tú","initials":"T","affinity":100,"mode":"AMISTAD","state":"CONNECTED"},
            "nodes":[{"id":"u1","label":"Laura","initials":"LP","affinity":87,"mode":"AMISTAD",
            "state":"CONNECTED"}],
            "edges":[{"otherId":"u1","mode":"AMISTAD","state":"CONNECTED","affinity":87}],
            "stats":{"connections":1,"simulated":0,"pending":0,"avgAffinity":87}},
            "plus":false}
            """.trimIndent()
        store.edit {
            it[stringPreferencesKey("parche_graph_v1")] = previous
            it[stringPreferencesKey("parche_graph_owner")] = "a"
        }

        val loaded = DataStoreGraphCache(store, ContractJson).load("a")

        assertEquals(87, loaded?.graph?.nodes?.single()?.measuredAffinity)
        assertEquals(87, loaded?.graph?.edges?.single()?.measuredAffinity)
    }

    @Test
    fun unmeasuredAffinitySurvivesTheCache() = runTest {
        val cache = cache()
        val content = ParcheSamples.content.let { c ->
            val unmeasured = c.graph.nodes.map { it.copy(affinity = 0, affinityMeasured = false) }
            c.copy(graph = c.graph.copy(nodes = unmeasured))
        }
        cache.save(content, "a")
        val loaded = cache.load("a")
        assertEquals(content, loaded)
        assertNull(loaded?.graph?.nodes?.first()?.measuredAffinity)
    }

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
