package me.santiagoreina.parcheu.feature.safety

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.reflect.KClass
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.MatchChoice
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.ScenarioTranscript
import me.santiagoreina.parcheu.contract.Speaker
import me.santiagoreina.parcheu.contract.TranscriptDto
import me.santiagoreina.parcheu.contract.Turn
import me.santiagoreina.parcheu.data.results.fullReport
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundScreen
import me.santiagoreina.parcheu.feature.conocer.match.MatchFoundUiState
import me.santiagoreina.parcheu.feature.conocer.match.testMatch
import me.santiagoreina.parcheu.feature.invites.FakeInvitesRepository
import me.santiagoreina.parcheu.feature.invites.InvitesActions
import me.santiagoreina.parcheu.feature.invites.InvitesScreen
import me.santiagoreina.parcheu.feature.invites.InvitesUiState
import me.santiagoreina.parcheu.feature.parche.NodeSheet
import me.santiagoreina.parcheu.feature.parche.ParcheCallbacks
import me.santiagoreina.parcheu.feature.parche.ParcheSamples
import me.santiagoreina.parcheu.feature.parche.ParcheScreen
import me.santiagoreina.parcheu.feature.parche.ParcheUiState
import me.santiagoreina.parcheu.feature.parche.fof.FofCallbacks
import me.santiagoreina.parcheu.feature.parche.fof.FofSamples
import me.santiagoreina.parcheu.feature.parche.fof.FofScreen
import me.santiagoreina.parcheu.feature.parche.fof.FofUiState
import me.santiagoreina.parcheu.feature.presentar.IntroProposalActions
import me.santiagoreina.parcheu.feature.presentar.IntroProposalContent
import me.santiagoreina.parcheu.feature.presentar.IntroProposalScreen
import me.santiagoreina.parcheu.feature.presentar.IntroProposalUiState
import me.santiagoreina.parcheu.feature.presentar.proposalView
import me.santiagoreina.parcheu.feature.results.DecisionState
import me.santiagoreina.parcheu.feature.results.ResultsActions
import me.santiagoreina.parcheu.feature.results.ResultsContent
import me.santiagoreina.parcheu.feature.results.ResultsScreen
import me.santiagoreina.parcheu.feature.results.ResultsUiState
import me.santiagoreina.parcheu.feature.results.TranscriptState
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio de E8.7: D2, F2, C5 (pendiente), E3 y D6 tienen una acción 'Reportar o bloquear' que abre H1
 * (y de ahí la ruta `Report`), y F8 y la transcripción tienen 'Reportar contenido de la IA'.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class SafetyEntryPointsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingNavigator : AppNavigator {
        val routes = mutableListOf<AppRoute>()
        override fun navigate(route: AppRoute) {
            routes += route
        }
        override fun navigateToTopLevel(route: AppRoute) = Unit
        override fun replaceAll(route: AppRoute) = Unit
        override fun back(): Boolean = true
        override fun canGoBack(): Boolean = true
        override fun navigateReplacing(route: AppRoute, replacing: KClass<out AppRoute>) = Unit
    }

    private fun clickReportOrBlock(index: Int = 0) {
        composeRule.onAllNodesWithContentDescription(REPORT_OR_BLOCK_DESCRIPTION)
            .get(index).performClick()
    }

    @Test
    fun d2NodeSheetHasReportOrBlock() {
        var clicks = 0
        val node = ParcheSamples.nodes.first { it.id == "and" }
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                ParcheScreen(
                    ParcheUiState.Loaded(
                        ParcheSamples.content,
                        sheet = NodeSheet(node, ParcheSamples.detail(node), loadingDetail = false)
                    ),
                    ParcheCallbacks(onReportOrBlock = { clicks++ }),
                    now = Instant.parse("2026-11-04T11:00:00Z"),
                    animate = false
                )
            }
        }
        composeRule.onNodeWithContentDescription(REPORT_OR_BLOCK_DESCRIPTION).assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun f2MatchFoundHasReportOrBlock() {
        var clicks = 0
        composeRule.setContent {
            ParcheUTheme {
                MatchFoundScreen(
                    MatchFoundUiState.Loaded(testMatch(), MatchChoice.SIMULATION),
                    {},
                    {},
                    {},
                    {},
                    {},
                    onReportOrBlock = { clicks++ }
                )
            }
        }
        composeRule.onNodeWithContentDescription(REPORT_OR_BLOCK_DESCRIPTION).assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun c5PendingInviteHasReportOrBlock() {
        val pending = FakeInvitesRepository.pendingLaura
        var reported: String? = null
        composeRule.setContent {
            ParcheUTheme {
                InvitesScreen(
                    InvitesUiState(
                        loading = false,
                        invite = FakeInvitesRepository.sampleInvite(listOf(pending))
                    ),
                    InvitesActions(onReportOrBlock = { reported = it.id })
                )
            }
        }
        clickReportOrBlock()
        assertEquals(pending.id, reported)
    }

    @Test
    fun e3ProposalHasReportOrBlock() {
        var clicks = 0
        composeRule.setContent {
            ParcheUTheme {
                IntroProposalScreen(
                    IntroProposalUiState.Loaded(IntroProposalContent(proposalView())),
                    IntroProposalActions(onReportOrBlock = { clicks++ })
                )
            }
        }
        composeRule.onNodeWithContentDescription(REPORT_OR_BLOCK_DESCRIPTION).assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun d6EachCandidateHasReportOrBlockWithItsAnonId() {
        val reported = mutableListOf<Pair<String, String>>()
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                FofScreen(
                    FofUiState.Unlocked(FofSamples.plus),
                    FofCallbacks(onReportOrBlock = { anonId, name -> reported += anonId to name }),
                    animate = false
                )
            }
        }
        val candidates = FofSamples.plus.candidates
        composeRule.onAllNodesWithContentDescription(REPORT_OR_BLOCK_DESCRIPTION)
            .assertCountEquals(candidates.size)
        composeRule.onAllNodesWithContentDescription(REPORT_OR_BLOCK_DESCRIPTION).onFirst()
            .performClick()
        assertEquals(candidates.first().anonId, reported.single().first)
        // La persona anónima nunca se nombra.
        assertEquals("Alguien", reported.single().second)
    }

    private fun results(transcript: TranscriptState? = null) = ResultsContent(
        otherLabel = "Alguien de la UIS",
        mode = Mode.ESTUDIO,
        isDemo = false,
        plus = true,
        report = fullReport(),
        decision = DecisionState.PENDING,
        transcript = transcript
    )

    @Test
    fun f8FullReportHasReportAiContent() {
        val turns = mutableListOf<Int>()
        composeRule.setContent {
            ParcheUTheme {
                ResultsScreen(
                    ResultsUiState.Loaded(results()),
                    ResultsActions(onReportAi = { turns += it })
                )
            }
        }
        composeRule.onNodeWithContentDescription(REPORT_AI_CONTENT_DESCRIPTION).assertIsDisplayed()
            .performClick()
        // El informe de F8 va con el turnIndex reservado, no con el turno 0 de la transcripción.
        assertEquals(listOf(REPORT_TURN_INDEX), turns)
    }

    @Test
    fun transcriptTurnsAreReportableWithAFlatTurnIndexAcrossScenarios() {
        val turns = mutableListOf<Int>()
        val transcript = TranscriptState.Loaded(
            TranscriptDto(
                listOf(
                    ScenarioTranscript(
                        "Café",
                        listOf(Turn(Speaker.ME, "Hola"), Turn(Speaker.OTHER, "¿Qué tal?"))
                    ),
                    ScenarioTranscript("Estudio", listOf(Turn(Speaker.OTHER, "Vamos a estudiar")))
                )
            )
        )
        composeRule.setContent {
            ParcheUTheme {
                ResultsScreen(
                    ResultsUiState.Loaded(results(transcript)),
                    ResultsActions(onReportAi = { turns += it })
                )
            }
        }
        composeRule.onAllNodesWithContentDescription(REPORT_AI_CONTENT_DESCRIPTION)
            .assertCountEquals(3)
        composeRule.onAllNodesWithContentDescription(
            REPORT_AI_CONTENT_DESCRIPTION
        )[2].performClick()
        composeRule.onAllNodesWithContentDescription(
            REPORT_AI_CONTENT_DESCRIPTION
        )[0].performClick()
        assertEquals(listOf(2, 0), turns)
    }

    @Test
    fun chosingReportInH1OpensTheReportRouteWithTheSameTarget() {
        val navigator = RecordingNavigator()
        val target = SafetyTarget(id = "a_x", name = "Alguien de la UIS", anonymous = true)
        var blocked = 0
        handleSafetyClosed(CloseReason.Report, target, navigator) { blocked++ }
        handleSafetyClosed(CloseReason.Dismissed, target, navigator) { blocked++ }
        assertEquals(
            listOf<AppRoute>(
                Report(userId = "a_x", chatId = null, name = "Alguien de la UIS", anonymous = true)
            ),
            navigator.routes
        )
        assertEquals(0, blocked)
        handleSafetyClosed(CloseReason.Blocked, target, navigator) { blocked++ }
        assertEquals(1, blocked)
        assertTrue(navigator.routes.size == 1)
    }

    @Test
    fun reportingAiContentOpensTheReportRouteForTheTurn() {
        val navigator = RecordingNavigator()
        navigator.reportAiContent("sim-1", 4)
        val route = navigator.routes.single() as Report
        assertEquals("sim-1", route.simulationId)
        assertEquals(4, route.turnIndex)
        assertTrue(route.toTarget().isAiContent)
    }
}
