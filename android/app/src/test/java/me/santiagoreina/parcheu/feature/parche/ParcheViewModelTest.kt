package me.santiagoreina.parcheu.feature.parche

import app.cash.turbine.test
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.plus.PlusSources
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Estados de D1-D5 con [FakeGraphRepository] (criterios de aceptación de E3.4). */
class ParcheViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun repo(vararg results: ApiResult<ParcheContent>, cached: ParcheContent? = null) =
        FakeGraphRepository(cached).apply { graphReturns(*results) }

    private fun loaded(vm: ParcheViewModel) = vm.state.value as ParcheUiState.Loaded

    @Test
    fun loadingThenLoaded() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository =
            object : GraphRepository by repo(ApiResult.Success(ParcheSamples.content)) {
                override suspend fun graph(community: String?): ApiResult<ParcheContent> {
                    gate.await()
                    return ApiResult.Success(ParcheSamples.content)
                }
            }
        val vm = ParcheViewModel(repository)
        vm.state.test {
            assertEquals(ParcheUiState.Loading, awaitItem())
            vm.start()
            gate.complete(Unit)
            assertEquals(ParcheUiState.Loaded(ParcheSamples.content), awaitItem())
        }
    }

    @Test
    fun emptyGraphShowsEmptyState() {
        val vm = ParcheViewModel(repo(ApiResult.Success(ParcheSamples.empty)))
        vm.start()
        assertEquals(ParcheUiState.Empty, vm.state.value)
    }

    @Test
    fun failureShowsErrorAndRetryGoesBackToLoading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = repo(failure(AppError.Network), ApiResult.Success(ParcheSamples.content))
        val repository =
            object : GraphRepository by fake {
                var calls = 0

                override suspend fun graph(community: String?): ApiResult<ParcheContent> {
                    if (calls++ == 1) gate.await()
                    return fake.graph(community)
                }
            }
        val vm = ParcheViewModel(repository)
        vm.start()
        assertEquals(ParcheUiState.Error(AppError.Network), vm.state.value)

        vm.retry()
        assertEquals(ParcheUiState.Loading, vm.state.value)
        gate.complete(Unit)
        assertTrue(vm.state.value is ParcheUiState.Loaded)
    }

    @Test
    fun cachedGraphIsShownImmediatelyAndNetworkFailureKeepsIt() {
        val gate = CompletableDeferred<Unit>()
        val fake = repo(failure(), cached = ParcheSamples.content)
        val repository =
            object : GraphRepository by fake {
                override suspend fun graph(community: String?): ApiResult<ParcheContent> {
                    gate.await()
                    return fake.graph(community)
                }
            }
        val vm = ParcheViewModel(repository)
        vm.start()
        // Pintado instantáneo: ya hay grafo aunque la red no haya respondido.
        assertEquals(ParcheUiState.Loaded(ParcheSamples.content), vm.state.value)
        gate.complete(Unit)
        assertTrue(vm.state.value is ParcheUiState.Loaded)
    }

    @Test
    fun cachedGraphIsReplacedByFreshOne() {
        val fresh = ParcheSamples.content.copy(plus = true)
        val vm = ParcheViewModel(repo(ApiResult.Success(fresh), cached = ParcheSamples.content))
        vm.start()
        assertEquals(fresh, loaded(vm).content)
    }

    @Test
    fun modeFilterDimsNodesOfOtherModes() {
        val vm = ParcheViewModel(repo(ApiResult.Success(ParcheSamples.content)))
        vm.start()
        assertTrue(loaded(vm).dimmedIds.isEmpty())

        vm.onModeFilter(Mode.ESTUDIO)

        val state = loaded(vm)
        val study = ParcheSamples.nodes.filter { it.mode == Mode.ESTUDIO }.map { it.id }
        assertEquals(ParcheSamples.nodes.map { it.id }.toSet() - study.toSet(), state.dimmedIds)
        // Atenuar no oculta: siguen los 9 nodos.
        assertEquals(9, state.nodes.size)

        vm.onModeFilter(null)
        assertTrue(loaded(vm).dimmedIds.isEmpty())
    }

    @Test
    fun affinityFilterHidesNodesBelowSeventy() {
        val vm = ParcheViewModel(repo(ApiResult.Success(ParcheSamples.content)))
        vm.start()

        vm.onAffinityFilter(true)

        val visible = loaded(vm).nodes
        assertTrue(visible.all { it.affinity >= 70 })
        assertEquals(listOf("val", "and", "seb", "cam", "lau"), visible.map { it.id })

        vm.onAffinityFilter(false)
        assertEquals(9, loaded(vm).nodes.size)
    }

    @Test
    fun communityFromRouteAsksApiAndClearingReloads() {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        val vm = ParcheViewModel(fake)
        vm.start(community = "melomano")
        assertEquals(listOf<String?>("melomano"), fake.graphCalls)
        assertEquals("melomano", loaded(vm).filters.communityId)

        vm.onClearCommunity()
        assertEquals(listOf<String?>("melomano", null), fake.graphCalls)
        assertNull(loaded(vm).filters.communityId)
    }

    @Test
    fun newCommunityFromReusedEntryFiltersEvenIfAlreadyStarted() {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        val vm = ParcheViewModel(fake)
        vm.start()
        assertNull(loaded(vm).filters.communityId)

        vm.start(community = "c1")
        assertEquals(listOf<String?>(null, "c1"), fake.graphCalls)
        assertEquals("c1", loaded(vm).filters.communityId)

        // Repetir el mismo valor (rotación) no vuelve a filtrar tras quitar la comunidad.
        vm.onClearCommunity()
        vm.start(community = "c1")
        assertEquals(listOf<String?>(null, "c1", null), fake.graphCalls)
        assertNull(loaded(vm).filters.communityId)
    }

    @Test
    fun tappingNodeOpensSheetWithDetail() {
        val vm = ParcheViewModel(repo(ApiResult.Success(ParcheSamples.content)))
        vm.start()

        vm.onNodeClick("and")

        val sheet = loaded(vm).sheet!!
        assertEquals("and", sheet.node.id)
        assertFalse(sheet.loadingDetail)
        assertEquals("sim-1", sheet.detail?.lastSimulation?.id)

        vm.onDismissSheet()
        assertNull(loaded(vm).sheet)
    }

    @Test
    fun sheetStaysUsableWhenDetailFails() {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        fake.detailResult = { failure() }
        val vm = ParcheViewModel(fake)
        vm.start()

        vm.onNodeClick("val")

        val sheet = loaded(vm).sheet!!
        assertNull(sheet.detail)
        assertFalse(sheet.loadingDetail)
    }

    @Test
    fun simulateAgainStartsSimulationAndOpensIt() = runTest {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        val vm = ParcheViewModel(fake)
        vm.start()
        vm.onNodeClick("and")
        vm.effects.test {
            vm.onSimulateAgain()
            assertEquals(ParcheEffect.Navigate(Simulation("sim-new")), awaitItem())
        }
        assertEquals(listOf("and" to Mode.ESTUDIO), fake.startedWith)
    }

    @Test
    fun simulateAgainFailureIsShownInSheet() {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        fake.startResult = failure(AppError.Network)
        val vm = ParcheViewModel(fake)
        vm.start()
        vm.onNodeClick("and")

        vm.onSimulateAgain()

        val sheet = loaded(vm).sheet!!
        assertEquals(AppError.Network, sheet.actionError)
        assertFalse(sheet.starting)
    }

    @Test
    fun simulateAgainQuotaExceededOpensG2() = runTest {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        val resetsAt = Instant.fromEpochSeconds(1_800_000_000)
        fake.startResult = failure(AppError.QuotaExceeded(QuotaKind.SIMULATION, resetsAt))
        val vm = ParcheViewModel(fake)
        vm.start()
        vm.onNodeClick("and")
        vm.effects.test {
            vm.onSimulateAgain()
            assertEquals(
                ParcheEffect.Navigate(
                    Paywall(PlusSources.limit(QuotaKind.SIMULATION, resetsAt))
                ),
                awaitItem()
            )
        }
        val sheet = loaded(vm).sheet!!
        assertNull(sheet.actionError)
        assertFalse(sheet.starting)
    }

    @Test
    fun simulateAgainPlusRequiredOpensG1() = runTest {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        fake.startResult = failure(AppError.PlusRequired)
        val vm = ParcheViewModel(fake)
        vm.start()
        vm.onNodeClick("and")
        vm.effects.test {
            vm.onSimulateAgain()
            assertEquals(
                ParcheEffect.Navigate(Paywall(PAYWALL_SOURCE_PARCHE)),
                awaitItem()
            )
        }
    }

    @Test
    fun chatAndResultNavigate() = runTest {
        val vm = ParcheViewModel(repo(ApiResult.Success(ParcheSamples.content)))
        vm.start()
        vm.onNodeClick("lau")
        vm.effects.test {
            vm.onChat()
            assertEquals(ParcheEffect.Navigate(Chat("chat-lau")), awaitItem())
            vm.onViewResult()
            assertEquals(ParcheEffect.Navigate(Results("sim-1")), awaitItem())
        }
    }

    @Test
    fun chatWithoutChatIdDoesNotNavigate() = runTest {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        fake.detailResult = { id ->
            ApiResult.Success(
                ParcheSamples.detail(ParcheSamples.nodes.first { it.id == id }).copy(chatId = null)
            )
        }
        val vm = ParcheViewModel(fake)
        vm.start()
        vm.onNodeClick("lau")
        vm.effects.test {
            vm.onChat()
            vm.onViewResult()
            // Solo llega "Ver resultado": "Chatear" sin chatId no navega.
            assertEquals(ParcheEffect.Navigate(Results("sim-1")), awaitItem())
        }
    }

    @Test
    fun startingTwiceKeepsFirstLoad() {
        val fake = repo(ApiResult.Success(ParcheSamples.content))
        val vm = ParcheViewModel(fake)
        vm.start()
        vm.start()
        assertEquals(1, fake.graphCalls.size)
    }
}
