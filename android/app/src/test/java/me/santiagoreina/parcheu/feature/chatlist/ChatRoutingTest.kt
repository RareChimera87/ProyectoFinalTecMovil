package me.santiagoreina.parcheu.feature.chatlist

import me.santiagoreina.parcheu.contract.ChatType
import me.santiagoreina.parcheu.navigation.Chat
import me.santiagoreina.parcheu.navigation.ScenarioChat
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRoutingTest {
    @Test
    fun scenarioChatsOpenScenarioScreen() {
        assertEquals(ScenarioChat("c1"), routeForChat("c1", ChatType.SCENARIO))
    }

    @Test
    fun directAndUnknownChatsOpenF9() {
        assertEquals(Chat("c1"), routeForChat("c1", ChatType.DIRECT))
        assertEquals(Chat("c1"), routeForChat("c1", null))
    }
}
