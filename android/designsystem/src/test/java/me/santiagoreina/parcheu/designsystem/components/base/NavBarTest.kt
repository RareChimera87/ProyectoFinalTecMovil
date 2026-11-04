package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NavBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val keys = listOf("home", "parche", "conocer", "chats", "perfil")

    private fun setBar(active: String, unread: Int, onSelect: (String) -> Unit = {}) {
        composeRule.setContent {
            ParcheUTheme {
                val icon = testIcon()
                ParcheUNavBar(
                    items = keys.map {
                        NavBarItem(
                            it,
                            "L-$it",
                            icon,
                            unread = if (it ==
                                "chats"
                            ) {
                                unread
                            } else {
                                0
                            }
                        )
                    },
                    activeKey = active,
                    onSelect = onSelect
                )
            }
        }
    }

    @Test
    fun resaltaSoloElItemActivo() {
        setBar("conocer", unread = 0)
        keys.forEach { key ->
            val node = composeRule.onNodeWithText("L-$key")
            if (key == "conocer") node.assertIsSelected() else node.assertIsNotSelected()
        }
    }

    @Test
    fun elActivoCambiaConLaSeleccion() {
        var active by mutableStateOf("home")
        composeRule.setContent {
            ParcheUTheme {
                val icon = testIcon()
                ParcheUNavBar(
                    items = keys.map { NavBarItem(it, "L-$it", icon) },
                    activeKey = active,
                    onSelect = { active = it }
                )
            }
        }
        composeRule.onNodeWithText("L-home").assertIsSelected()
        composeRule.onNodeWithText("L-perfil").performClick()
        composeRule.onNodeWithText("L-perfil").assertIsSelected()
        composeRule.onNodeWithText("L-home").assertIsNotSelected()
    }

    @Test
    fun cambiarDeItemInvocaElCallbackConLaClave() {
        var selected = "home"
        setBar("home", 0) { selected = it }
        composeRule.onNodeWithText("L-conocer").performClick()
        assertEquals("conocer", selected)
    }

    @Test
    fun sinNoLeidosNoMuestraBadge() {
        setBar("home", unread = 0)
        composeRule.onNodeWithTag(
            navBarBadgeTag("chats"),
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun conNoLeidosMuestraBadgeSoloEnChats() {
        setBar("home", unread = 3)
        composeRule.onNodeWithTag(
            navBarBadgeTag("chats"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            navBarBadgeTag("home"),
            useUnmergedTree = true
        ).assertDoesNotExist()
        composeRule.onNodeWithText("3", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun masDeNoventaYNueveMuestraTope() {
        setBar("home", unread = 150)
        composeRule.onNodeWithText("99+", useUnmergedTree = true).assertIsDisplayed()
    }
}
