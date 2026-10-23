package me.santiagoreina.parcheu.feature.invites

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** C5 con su ViewModel y un FakeCodeScanner: lo que ve la persona al escanear, agregar y aceptar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InvitesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private fun show(vm: InvitesViewModel, scanner: CodeScanner) {
        composeRule.setContent {
            ParcheUTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                InvitesScreen(
                    state,
                    InvitesActions(
                        onTab = vm::onTab,
                        onCodeChange = vm::onCodeChange,
                        onMode = vm::onMode,
                        onAdd = vm::onAdd,
                        onScan = { vm.onScan(scanner) },
                        onAccept = vm::onAccept
                    )
                )
            }
        }
    }

    @Test
    fun scanningOurQrPreloadsTheCodeAndAForeignOneIsRejected() {
        val vm = InvitesViewModel(FakeInvitesRepository(), null, { null }, "parcheu-dev.web.app")
        val scanner = FakeCodeScanner(ScanResult.Scanned("https://parcheu-dev.web.app/i/SEBA-1290"))
        show(vm, scanner)

        composeRule.onNodeWithText("Escanear").performClick()
        composeRule.onNodeWithTag(InvitesTags.SCAN).performClick()
        composeRule.onNodeWithTag(InvitesTags.INPUT).assertTextContains("SEBA-1290")

        scanner.result = ScanResult.Scanned("https://otra-app.example/qr/42")
        composeRule.onNodeWithTag(InvitesTags.SCAN).performClick()
        composeRule.onNodeWithTag(InvitesTags.MESSAGE).performScrollTo()
            .assertTextEquals("Este código no es de ParcheU")
        assertEquals(2, scanner.scans)
    }

    @Test
    fun myCodeShowsTheQrTheCodeAndAcceptsPendingInvites() {
        val pending = FakeInvitesRepository.pendingLaura
        val repo =
            FakeInvitesRepository(
                invite = ApiResult.Success(FakeInvitesRepository.sampleInvite(listOf(pending)))
            )
        val vm = InvitesViewModel(repo, null, { "Mariana Gómez" }, "parcheu-dev.web.app")
        show(vm, FakeCodeScanner(ScanResult.Cancelled))

        composeRule.onNodeWithTag(InvitesTags.QR).assertIsDisplayed()
        composeRule.onNodeWithTag(
            InvitesTags.CODE,
            useUnmergedTree = true
        ).assertTextEquals("MARI-4827")
        composeRule.onNodeWithTag(InvitesTags.accept(pending.id)).performScrollTo().performClick()
        assertEquals(listOf(pending.id to pending.mode), repo.accepted)
        composeRule.onNodeWithTag(
            InvitesTags.MESSAGE
        ).performScrollTo().assertTextContains("Laura Pérez", substring = true)
    }

    @Test
    fun addingRedeemsInTheChosenMode() {
        val repo = FakeInvitesRepository()
        val vm = InvitesViewModel(repo, "SEBA-1290", { null }, "parcheu-dev.web.app")
        show(vm, FakeCodeScanner(ScanResult.Cancelled))
        composeRule.onNodeWithTag(InvitesTags.mode(Mode.ESTUDIO)).performScrollTo().performClick()
        composeRule.onNodeWithTag(InvitesTags.ADD).performScrollTo().performClick()
        assertEquals(listOf("SEBA-1290" to Mode.ESTUDIO), repo.redeemed)
    }
}
