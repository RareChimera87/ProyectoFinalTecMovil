package me.santiagoreina.parcheu.push

import me.santiagoreina.parcheu.contract.NotificationKinds
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.ChatList
import me.santiagoreina.parcheu.navigation.Communities
import me.santiagoreina.parcheu.navigation.IntroProposal
import me.santiagoreina.parcheu.navigation.MatchFound
import me.santiagoreina.parcheu.navigation.MatchQueue
import me.santiagoreina.parcheu.navigation.Notifications
import me.santiagoreina.parcheu.navigation.Parche
import me.santiagoreina.parcheu.navigation.Results
import me.santiagoreina.parcheu.navigation.Simulation
import me.santiagoreina.parcheu.navigation.Simulations
import me.santiagoreina.parcheu.navigation.TrainTwin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tabla de casos: cada tipo de notificación (con la ruta que manda el backend) abre la pantalla correcta. */
class DeepLinksTest {
    private data class Case(val type: String, val route: String?, val expected: AppRoute)

    private val cases =
        listOf(
            Case(NotificationKinds.SIMULATION_READY, "parcheu://results/sim-1", Results("sim-1")),
            Case(NotificationKinds.SIMULATION_FAILED, "parcheu://simulation/sim-2", Simulation("sim-2")),
            Case(NotificationKinds.MATCH_FOUND, "parcheu://match/m-3", MatchFound("m-3")),
            Case(NotificationKinds.MATCH_EXPIRED, "parcheu://match", MatchQueue()),
            Case(NotificationKinds.CONNECT_REQUEST, "parcheu://notifications", Notifications),
            Case(NotificationKinds.NEW_CONNECTION, "parcheu://parche", Parche()),
            Case(NotificationKinds.INTRODUCTION_PROPOSAL, "parcheu://intro/i-4", IntroProposal("i-4")),
            Case(NotificationKinds.INVITE_PENDING, "parcheu://add-friends", AddFriends()),
            Case(NotificationKinds.CHAT_MESSAGE, "parcheu://chat/c-5", Chat("c-5")),
            Case(NotificationKinds.TWIN_MEMORIES, "parcheu://train-twin", TrainTwin),
            Case(NotificationKinds.TWIN_FEEDBACK, "parcheu://train-twin", TrainTwin),
            Case(NotificationKinds.COMMUNITY, "parcheu://communities", Communities),
            Case(NotificationKinds.ACCOUNT, "parcheu://notifications", Notifications),
        )

    @Test
    fun everyKnownKindWithItsRouteOpensTheRightScreen() {
        cases.forEach { assertEquals(it.type, it.expected, DeepLinks.resolve(it.type, it.route)) }
    }

    @Test
    fun theTableCoversEveryKindOfTheContract() {
        val declared = NotificationKinds::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
        assertEquals(declared.toSet(), cases.map { it.type }.toSet())
    }

    @Test
    fun withoutRouteEachKindFallsBackToItsScreen() {
        val expected =
            mapOf(
                NotificationKinds.SIMULATION_READY to Simulations,
                NotificationKinds.SIMULATION_FAILED to Simulations,
                NotificationKinds.MATCH_FOUND to MatchQueue(),
                NotificationKinds.MATCH_EXPIRED to MatchQueue(),
                NotificationKinds.CONNECT_REQUEST to Notifications,
                NotificationKinds.NEW_CONNECTION to Parche(),
                NotificationKinds.INTRODUCTION_PROPOSAL to Notifications,
                NotificationKinds.INVITE_PENDING to AddFriends(),
                NotificationKinds.CHAT_MESSAGE to ChatList,
                NotificationKinds.TWIN_MEMORIES to TrainTwin,
                NotificationKinds.TWIN_FEEDBACK to TrainTwin,
                NotificationKinds.COMMUNITY to Communities,
                NotificationKinds.ACCOUNT to Notifications,
            )
        expected.forEach { (type, route) -> assertEquals(type, route, DeepLinks.resolve(type, null)) }
    }

    @Test
    fun unknownTypeOrBrokenRouteOpensTheInbox() {
        assertEquals(Notifications, DeepLinks.resolve("TIPO_NUEVO", null))
        assertEquals(Notifications, DeepLinks.resolve(null, null))
        assertEquals(Notifications, DeepLinks.resolve("TIPO_NUEVO", "parcheu://pantalla-que-no-existe"))
    }

    @Test
    fun aKnownTypeWithAnInvalidRouteUsesTheTypeFallback() {
        assertEquals(ChatList, DeepLinks.resolve(NotificationKinds.CHAT_MESSAGE, "parcheu://chat"))
        assertEquals(
            Simulations,
            DeepLinks.resolve(NotificationKinds.SIMULATION_READY, "https://evil.example/results/1"),
        )
    }

    @Test
    fun rejectsLinksThatAreNotOurScreens() {
        assertNull(DeepLinks.fromUri(null))
        assertNull(DeepLinks.fromUri(""))
        assertNull(DeepLinks.fromUri("https://parcheu.app/chat/1"))
        assertNull(DeepLinks.fromUri("parcheu://chat/a/b"))
        assertNull(DeepLinks.fromUri("parcheu://chat/..%2Fadmin"))
        assertNull(DeepLinks.fromUri("parcheu://chat/${"x".repeat(65)}"))
        assertNull(DeepLinks.fromUri("parcheu://results"))
        assertNull(DeepLinks.fromUri("no es una uri"))
    }

    @Test
    fun addFriendsLinkCarriesTheInviteCode() {
        assertEquals(AddFriends(code = "ABCD-1234"), DeepLinks.fromUri("parcheu://add-friends/ABCD-1234"))
    }
}
