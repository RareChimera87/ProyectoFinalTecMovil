package me.santiagoreina.parcheu.feature.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.navigation.IntroProposal
import me.santiagoreina.parcheu.navigation.Paywall
import me.santiagoreina.parcheu.navigation.Results
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val inbox = NotificationsSamples.withUnread.inbox

    private fun loaded(vm: NotificationsViewModel) = vm.state.value as NotificationsUiState.Loaded

    @Test
    fun loadsInboxOnStart() {
        val vm = NotificationsViewModel(FakeNotificationsRepository(ApiResult.Success(inbox)))
        assertEquals(inbox, loaded(vm).inbox)
        assertEquals(3, loaded(vm).unread)
    }

    @Test
    fun errorOnFirstLoadAndRetry() {
        val repo =
            FakeNotificationsRepository(
                ApiResult.Failure(AppError.Network),
                ApiResult.Success(inbox)
            )
        val vm = NotificationsViewModel(repo)
        assertEquals(NotificationsUiState.Error(AppError.Network), vm.state.value)
        vm.load()
        assertEquals(inbox, loaded(vm).inbox)
    }

    @Test
    fun openingNavigatesToItsRouteAndMarksItRead() = runTest {
        val repo = FakeNotificationsRepository(ApiResult.Success(inbox))
        val vm = NotificationsViewModel(repo)

        vm.onOpen(NotificationsSamples.resultReady)

        assertEquals(NotificationsEffect.Navigate(Results("sim-daniela")), vm.effects.first())
        assertEquals(listOf(listOf("n-result")), repo.markReadCalls)
        assertTrue(loaded(vm).inbox.today.first { it.id == "n-result" }.read)
        assertEquals(2, loaded(vm).unread)

        vm.onOpen(NotificationsSamples.introduction)
        assertEquals(NotificationsEffect.Navigate(IntroProposal("intro-1")), vm.effects.first())
    }

    @Test
    fun openingAReadItemDoesNotCallTheBackendAgain() = runTest {
        val repo = FakeNotificationsRepository(ApiResult.Success(inbox))
        val vm = NotificationsViewModel(repo)
        vm.onOpen(NotificationsSamples.twin)
        vm.effects.first()
        assertTrue(repo.markReadCalls.isEmpty())
    }

    @Test
    fun lockedItemOpensThePaywall() = runTest {
        val vm = NotificationsViewModel(FakeNotificationsRepository(ApiResult.Success(inbox)))
        vm.onOpen(NotificationsSamples.lockedConnect)
        assertEquals(
            NotificationsEffect.Navigate(Paywall(PAYWALL_SOURCE_NOTIFICATIONS)),
            vm.effects.first()
        )
    }

    @Test
    fun itemWhoseRouteIsTheInboxStaysHere() = runTest {
        val repo = FakeNotificationsRepository(ApiResult.Success(inbox))
        val vm = NotificationsViewModel(repo)
        vm.onOpen(NotificationsSamples.lockedConnect.copy(locked = false))
        assertNull(withTimeoutOrNull(100) { vm.effects.first() })
        assertEquals(listOf(listOf("n-locked")), repo.markReadCalls)
    }

    @Test
    fun markAllReadIsOptimisticAndSendsTheShownUnreadIds() {
        val repo = FakeNotificationsRepository(ApiResult.Success(inbox))
        val vm = NotificationsViewModel(repo)
        vm.onMarkAllRead()
        val shownUnread = (inbox.today + inbox.week + inbox.older).filterNot {
            it.read
        }.map { it.id }
        assertEquals(3, shownUnread.size)
        assertEquals(listOf<List<String>?>(shownUnread), repo.markReadCalls)
        assertEquals(0, loaded(vm).unread)
        assertNull(loaded(vm).actionError)
    }

    @Test
    fun markAllReadRevertsOnFailure() {
        val repo =
            FakeNotificationsRepository(
                ApiResult.Success(inbox),
                markReadResult = ApiResult.Failure(AppError.Network)
            )
        val vm = NotificationsViewModel(repo)
        vm.onMarkAllRead()
        assertEquals(3, loaded(vm).unread)
        assertEquals(AppError.Network, loaded(vm).actionError)
        vm.onDismissError()
        assertNull(loaded(vm).actionError)
    }

    @Test
    fun failedMarkAllReadKeepsItemsOpenedMeanwhileAndTheLatestRefresh() {
        // Lo que devuelve el backend tras abrir 'n-result': ese ya leído y un aviso nuevo.
        val refreshed =
            inbox.copy(
                today =
                inbox.today.map { if (it.id == "n-result") it.copy(read = true) else it } +
                    NotificationsSamples.twin.copy(id = "n-nuevo", read = false)
            )
        val gate = CompletableDeferred<Unit>()
        val repo =
            FakeNotificationsRepository(
                ApiResult.Success(inbox),
                ApiResult.Success(refreshed),
                markReadResult = ApiResult.Failure(AppError.Network),
                markAllGate = gate
            )
        val vm = NotificationsViewModel(repo)
        vm.onMarkAllRead()
        // Mientras 'Marcar leídas' está en vuelo: se abre un aviso (marcado bien) y llega un refresco.
        vm.onOpen(NotificationsSamples.resultReady)
        vm.load()
        gate.complete(Unit)

        val state = loaded(vm)
        assertEquals(AppError.Network, state.actionError)
        assertTrue(state.all.first { it.id == "n-result" }.read)
        assertTrue(state.all.any { it.id == "n-nuevo" })
        assertEquals(inbox.today.size + 1, state.inbox.today.size)
        // Vuelven a no leídas solo las que lo eran antes y nadie abrió.
        assertEquals(2, state.all.count { !it.read && it.id != "n-nuevo" })
    }

    @Test
    fun markAllReadWithNothingUnreadDoesNothing() {
        val repo =
            FakeNotificationsRepository(ApiResult.Success(NotificationsSamples.allRead.inbox))
        val vm = NotificationsViewModel(repo)
        vm.onMarkAllRead()
        assertTrue(repo.markReadCalls.isEmpty())
    }
}
