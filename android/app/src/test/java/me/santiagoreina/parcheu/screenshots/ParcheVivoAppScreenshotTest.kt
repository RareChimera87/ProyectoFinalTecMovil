package me.santiagoreina.parcheu.screenshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import me.santiagoreina.parcheu.R
import me.santiagoreina.parcheu.designsystem.components.compound.DuotonePhoto
import me.santiagoreina.parcheu.designsystem.theme.ParcheUPalette
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base de la dirección A "Parche Vivo" en :app: el duotono sobre los WebP reales de drawable-nodpi (B1) y
 * el ícono adaptativo (fondo + primer plano + monocromo) tal como lo arma el launcher. Referencias en
 * src/test/screenshots/parchevivo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class ParcheVivoAppScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun duotoneOnTwinQuestionPhotos() {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                val c = ParcheUTheme.colors
                Column(
                    Modifier.background(c.bg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        R.drawable.twin_q01_a to c.friendship,
                        R.drawable.twin_q04_b to c.study,
                        R.drawable.twin_q07_a to c.couple
                    ).forEach { (photo, mode) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(
                                painterResource(photo),
                                contentDescription = null,
                                modifier =
                                Modifier.width(
                                    160.dp
                                ).height(104.dp).clip(RoundedCornerShape(20.dp))
                            )
                            DuotonePhoto(
                                painter = painterResource(photo),
                                mode = mode,
                                contentDescription = null,
                                modifier = Modifier.width(160.dp).height(104.dp)
                            )
                        }
                    }
                }
            }
        }
        capture("duotone_webp")
    }

    @Test
    fun launcherIconLayers() {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                Row(
                    Modifier.background(ParcheUTheme.colors.bg).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // El launcher muestra los 72 dp centrales de la capa de 108: se dibuja a 144 dp y se
                    // recorta a 96, con máscara redonda y "squircle" aproximado.
                    listOf(CircleShape, RoundedCornerShape(30)).forEach { shape ->
                        Box(Modifier.size(96.dp).clip(shape), contentAlignment = Alignment.Center) {
                            Image(
                                painterResource(R.drawable.ic_launcher_background),
                                null,
                                Modifier.requiredSize(144.dp)
                            )
                            Image(
                                painterResource(R.drawable.ic_launcher_foreground),
                                null,
                                Modifier.requiredSize(144.dp)
                            )
                        }
                    }
                    // Ícono temático: el monocromo teñido sobre un fondo claro del sistema.
                    Box(
                        Modifier.size(
                            96.dp
                        ).clip(CircleShape).background(ParcheUPalette.LightPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painterResource(R.drawable.ic_launcher_monochrome),
                            null,
                            Modifier.requiredSize(144.dp),
                            colorFilter = ColorFilter.tint(ParcheUPalette.LightOnPrimaryContainer)
                        )
                    }
                }
            }
        }
        capture("launcher_icon")
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/parchevivo/$name.png",
            roborazziOptions =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }
}
