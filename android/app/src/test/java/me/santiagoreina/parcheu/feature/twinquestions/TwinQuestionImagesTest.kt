package me.santiagoreina.parcheu.feature.twinquestions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import java.io.File
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fotos de B1: las 12 preguntas de la semilla tienen las dos; una pregunta nueva queda sin foto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class TwinQuestionImagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Ids de `insert into questions` en la semilla del backend, si el repo completo está presente. */
    private fun seedIdsFromSql(): List<Int>? {
        val sql = listOf(File("../../backend"), File("../backend"), File("backend"))
            .map { File(it, "src/main/resources/db/migration/V2__semillas.sql") }
            .firstOrNull { it.isFile }
            ?.readText() ?: return null
        val block = sql.substringAfter("insert into questions").substringBefore(";")
        return Regex("""\(\s*(\d+)\s*,\s*\d+\s*,\s*'""").findAll(block).map {
            it.groupValues[1].toInt()
        }.toList()
    }

    @Test
    fun lasDocePreguntasDeLaSemillaTienenAmbasFotos() {
        val ids = seedQuestions.map { it.id }
        assertEquals((1..12).toList(), ids)
        seedIdsFromSql()?.let { assertEquals("ids de V2__semillas.sql", ids, it) }
        val resources =
            ApplicationProvider.getApplicationContext<android.content.Context>().resources
        ids.forEach { id ->
            val images = TwinQuestionImages.forQuestion(id)
            assertNotNull("pregunta $id sin fotos", images)
            images!!
            assertNotEquals(images.a, images.b)
            val two = "%02d".format(id)
            assertEquals("twin_q${two}_a", resources.getResourceEntryName(images.a))
            assertEquals("twin_q${two}_b", resources.getResourceEntryName(images.b))
        }
        val all = ids.flatMap { TwinQuestionImages.forQuestion(it)!!.let { i -> listOf(i.a, i.b) } }
        assertEquals("24 fotos distintas", 24, all.toSet().size)
    }

    @Test
    fun unIdDesconocidoNoTieneFotos() {
        assertNull(TwinQuestionImages.forQuestion(13))
        assertNull(TwinQuestionImages.forQuestion(0))
        assertNull(TwinQuestionImages.forQuestion(-1))
    }

    @Test
    fun lasTarjetasMuestranLaFotoDeCadaOpcion() {
        setQuestion(seedQuestions.first())
        composeRule.onNodeWithTag(
            "twinQuestions:optionA:image",
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            "twinQuestions:optionB:image",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    @Test
    fun unaPreguntaNuevaSinFotoSeVeSoloConTexto() {
        setQuestion(seedQuestions.first().copy(id = 99))
        composeRule.onAllNodesWithTag("twinQuestions:optionA:image", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("twinQuestions:optionB:image", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("twinQuestions:optionA").assertIsDisplayed()
        composeRule.onNodeWithTag("twinQuestions:optionB").assertIsDisplayed()
    }

    private fun setQuestion(question: me.santiagoreina.parcheu.contract.QuestionDto) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = false) {
                TwinQuestionsContent(
                    state = TwinQuestionsUiState(
                        phase = TwinQuestionsPhase.Answering,
                        questions = listOf(question),
                        answers = emptyMap()
                    ),
                    onAnswer = {},
                    onClose = {},
                    onRetry = {}
                )
            }
        }
    }
}
