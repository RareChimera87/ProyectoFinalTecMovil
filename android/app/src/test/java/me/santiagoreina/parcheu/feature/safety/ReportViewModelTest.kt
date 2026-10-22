package me.santiagoreina.parcheu.feature.safety

import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.ReportReason
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportViewModelTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repo = FakeSafetyRepository()

    @Test
    fun cannotSubmitWithoutReason() = runTest {
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        assertTrue(repo.reports.isEmpty())
    }

    @Test
    fun submitReportsWithChatAndBlocksByDefault() = runTest {
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        vm.selectReason(ReportReason.HARASSMENT)
        vm.onDetailsChange("  me insultó  ")
        vm.submit()
        assertEquals(
            listOf(
                FakeSafetyRepository.ReportCall(
                    FakeSafetyRepository.laura,
                    ReportReason.HARASSMENT,
                    "  me insultó  ",
                    "chat-1",
                ),
            ),
            repo.reports,
        )
        assertEquals(listOf(FakeSafetyRepository.laura), repo.blocks)
        val sent = vm.state.value.sent!!
        assertTrue(sent.blocked)
        assertTrue(sent.helpResources.isEmpty())
    }

    @Test
    fun withoutAttachingMessagesTheChatIsNotSentAndBlockCanBeSkipped() = runTest {
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        vm.selectReason(ReportReason.FAKE_OR_SCAM)
        vm.setAttachMessages(false)
        vm.setAlsoBlock(false)
        vm.submit()
        assertNull(repo.reports.single().chatId)
        assertTrue(repo.blocks.isEmpty())
        assertFalse(vm.state.value.sent!!.blocked)
    }

    @Test
    fun selfHarmDoesNotBlockByDefaultButRespectsAnExplicitChoice() = runTest {
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        vm.selectReason(ReportReason.SELF_HARM)
        assertFalse(vm.state.value.alsoBlock)
        vm.selectReason(ReportReason.HARASSMENT)
        assertTrue(vm.state.value.alsoBlock)
        vm.selectReason(ReportReason.SELF_HARM)
        vm.setAlsoBlock(true)
        vm.selectReason(ReportReason.HARASSMENT)
        vm.selectReason(ReportReason.SELF_HARM)
        assertTrue(vm.state.value.alsoBlock)
        vm.submit()
        assertEquals(1, repo.blocks.size)
    }

    @Test
    fun selfHarmShowsHelpResources() = runTest {
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        vm.selectReason(ReportReason.SELF_HARM)
        vm.submit()
        assertEquals(listOf("192", "106", "123"), vm.state.value.sent!!.helpResources.map { it.phone })
    }

    @Test
    fun reportFailureKeepsTheFormWithTheErrorAndDoesNotBlock() = runTest {
        repo.reportResult = FakeSafetyRepository.failure
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        vm.selectReason(ReportReason.OTHER)
        vm.submit()
        assertEquals(AppError.Network, vm.state.value.error)
        assertNull(vm.state.value.sent)
        assertTrue(repo.blocks.isEmpty())
    }

    @Test
    fun blockFailureAfterReportIsReported() = runTest {
        repo.blockResult = FakeSafetyRepository.failure
        val vm = ReportViewModel(FakeSafetyRepository.laura, repo)
        vm.selectReason(ReportReason.OTHER)
        vm.submit()
        val sent = vm.state.value.sent!!
        assertFalse(sent.blocked)
        assertTrue(sent.blockFailed)
    }

    @Test
    fun withoutChatThereIsNothingToAttach() = runTest {
        val vm = ReportViewModel(FakeSafetyRepository.laura.copy(chatId = null), repo)
        assertFalse(vm.state.value.canAttach)
        vm.setAttachMessages(true)
        assertFalse(vm.state.value.attachMessages)
    }

    @Test
    fun anonymousTargetIsSentAsAnonId() {
        val request = SafetyTarget(
            "a_xyz",
            "Alguien de la UIS",
            anonymous = true,
        ).toRequest(ReportReason.OTHER, " ", "c1")
        assertEquals("a_xyz", request.anonId)
        assertNull(request.targetUserId)
        assertNull(request.details)
        val named = FakeSafetyRepository.laura.toRequest(ReportReason.OTHER, "x", null)
        assertEquals("user-2", named.targetUserId)
        assertNull(named.anonId)
    }
}
