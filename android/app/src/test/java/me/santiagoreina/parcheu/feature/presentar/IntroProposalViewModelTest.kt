package me.santiagoreina.parcheu.feature.presentar

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.IntroductionStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** E3 (E6.2): pendiente, aceptado (esperando), rechazado y conectados con chat. */
class IntroProposalViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeIntroductionsRepository().apply {
        introductionFallback = ApiResult.Success(proposalView())
    }

    private fun IntroProposalViewModel.content() =
        (state.value as IntroProposalUiState.Loaded).content

    @Test
    fun aNewProposalIsPendingAndTheOtherPersonIsAnonymous() {
        val vm = IntroProposalViewModel("intro-1", repo)
        assertEquals(ProposalState.PENDING, vm.content().state)
        assertNull(vm.content().introduction.other.id)
        assertEquals("Alguien de la UIS", vm.content().introduction.other.label)
    }

    @Test
    fun interestedWaitsForTheOtherPerson() {
        repo.respondResult = { ApiResult.Success(proposalView(myResponse = true)) }
        val vm = IntroProposalViewModel("intro-1", repo)
        vm.onInterested()
        assertEquals(listOf(true), repo.respondCalls)
        assertEquals(ProposalState.WAITING, vm.content().state)
        vm.onDecline()
        assertEquals(1, repo.respondCalls.size)
    }

    @Test
    fun declineIsFinal() {
        repo.respondResult =
            { ApiResult.Success(proposalView(IntroductionStatus.DECLINED, myResponse = false)) }
        val vm = IntroProposalViewModel("intro-1", repo)
        vm.onDecline()
        assertEquals(listOf(false), repo.respondCalls)
        assertEquals(ProposalState.DECLINED, vm.content().state)
    }

    @Test
    fun whenBothAcceptTheChatOpens() = runTest {
        repo.respondResult = {
            ApiResult.Success(
                proposalView(
                    IntroductionStatus.CONNECTED,
                    myResponse = true,
                    report = introFullReport("chat-9")
                )
            )
        }
        val vm = IntroProposalViewModel("intro-1", repo)
        vm.onInterested()
        assertEquals(ProposalState.CONNECTED, vm.content().state)
        assertEquals(IntroProposalEffect.OpenChat("chat-9"), vm.effects.first())
    }

    @Test
    fun theOtherPersonsNoIsNeverShown() {
        // El servidor devuelve PROPOSED aunque la otra persona haya dicho que no: se espera.
        repo.introductionFallback = ApiResult.Success(proposalView(myResponse = true))
        val vm = IntroProposalViewModel("intro-1", repo)
        assertEquals(ProposalState.WAITING, vm.content().state)
    }

    @Test
    fun blockedShowsTheError() {
        repo.respondResult = { ApiResult.Failure(AppError.Blocked) }
        val vm = IntroProposalViewModel("intro-1", repo)
        vm.onInterested()
        assertEquals(AppError.Blocked, vm.content().error)
        assertEquals(ProposalState.PENDING, vm.content().state)
        vm.onDismissError()
        assertNull(vm.content().error)
    }

    @Test
    fun notFoundIsAnError() {
        repo.introductionFallback = ApiResult.Failure(AppError.NotFound)
        val vm = IntroProposalViewModel("intro-1", repo)
        assertEquals(IntroProposalUiState.Error(AppError.NotFound), vm.state.value)
    }
}
