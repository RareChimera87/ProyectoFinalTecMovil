package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.brand.ParcheUAppIcon
import me.santiagoreina.parcheu.designsystem.brand.ParcheULogo
import me.santiagoreina.parcheu.designsystem.brand.WordmarkSize
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipSize
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipStyle
import me.santiagoreina.parcheu.designsystem.components.base.ModeFilterChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeSpineCard
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascot
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotSize
import me.santiagoreina.parcheu.designsystem.mascot.TwinMascotTone
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Galería de los componentes de la dirección A "Parche Vivo". La usan los @Preview de abajo y las
 * capturas Roborazzi de `ParcheVivoScreenshotTest` (src/test/screenshots/parchevivo).
 */
internal object ParcheVivoGallery {
    @Composable
    fun Heroes() = GalleryColumn {
        HeroBlock(
            title = "2",
            titleSuffix = "libres de 5",
            overline = "Simulaciones · esta semana",
            subtitle = "Usaste 3. Se renuevan el lunes.",
            badge = { ParcheUPlusBadge(onHero = true) }
        ) {
            QuotaMascots(used = 3, limit = 5)
        }
        HeroBlock(
            title = "Sin límite",
            titleSize = HeroTitleSize.Headline,
            overline = "Simulaciones",
            subtitle = "Llevas 4 hoy."
        ) {
            QuotaMascots(used = 4, limit = null)
        }
        HeroBlock(
            title = "0",
            titleSuffix = "libres de 5",
            overline = "Simulaciones · esta semana",
            subtitle = "Ya usaste las 5 · se renuevan el lunes",
            action = {
                TonalButton(
                    text = "Simula sin límite con ParcheU+",
                    onClick = {},
                    size = ParcheUButtonSize.Small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            QuotaMascots(used = 5, limit = 5)
        }
        HeroBlock(
            title = "88 %",
            overline = "Afinidad en Estudio",
            subtitle = "Andrés y tú: ya están conectados.",
            colors = HeroBlockDefaults.modeColors(ParcheUTheme.colors.study)
        )
    }

    @Composable
    fun SpineCards() = GalleryColumn {
        val c = ParcheUTheme.colors
        listOf(
            Triple("Tú y Laura conversan", "Escenario 2 de 3 · Estudio", c.study),
            Triple("Sebas", "72 % la última vez · Amistad", c.friendship),
            Triple("Valentina", "Resultado listo · Pareja", c.couple)
        ).forEach { (title, line, mode) ->
            ModeSpineCard(mode = mode, modifier = Modifier.fillMaxWidth(), onClick = {}) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.tx)
                Text(line, style = MaterialTheme.typography.bodySmall, color = c.mu)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccessTile(
                title = "Match al azar",
                subtitle = "Te queda 1 hoy",
                modifier = Modifier.weight(1.25f)
            )
            AccessTile(
                title = "Presentar",
                subtitle = "Junta a dos amigos",
                style = AccessTileStyle.Outline,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeFilterChip(label = "Todos", selected = true, onClick = {})
            ModeFilterChip(label = "Amistad", selected = false, onClick = {
            }, dotColor = c.friendship.solid)
            ModeFilterChip(label = "Estudio", selected = false, onClick = {
            }, dotColor = c.study.solid)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip("Amistad", c.friendship, size = ModeChipSize.S, style = ModeChipStyle.Tonal)
            ModeChip("Pareja", c.couple, size = ModeChipSize.S, style = ModeChipStyle.Tonal)
            ModeChip("Estudio", c.study, size = ModeChipSize.S, style = ModeChipStyle.Tonal)
            ModeChip("Estudio", c.study, size = ModeChipSize.S)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalButton("Simular otra vez", onClick = {}, size = ParcheUButtonSize.Medium)
            PrimaryButton("Chatear", onClick = {}, size = ParcheUButtonSize.Medium)
        }
    }

    @Composable
    fun Duotones() = GalleryColumn {
        val c = ParcheUTheme.colors
        val photo =
            BrushPainter(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF87CEEB),
                        Color(0xFFF4A460),
                        Color(0xFF2E8B57),
                        Color(0xFF1B1B1B)
                    )
                )
            )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(c.friendship, c.study, c.couple).forEach { mode ->
                DuotonePhoto(
                    painter = photo,
                    mode = mode,
                    contentDescription = null,
                    modifier = Modifier.weight(1f).height(96.dp)
                )
            }
        }
        // Modifier.duotone sobre un dibujo arbitrario (siluetas sobre cielo, como las fotos de B1).
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(ParcheUTheme.shapes.photo)
                .duotone(c.study)
        ) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7EC8E3), Color(0xFFFFE29A))))
            drawRect(Color(0xFF3B7A3B), topLeft = Offset(0f, size.height * 0.7f))
            listOf(0.2f, 0.4f, 0.6f, 0.8f).forEach { x ->
                drawCircle(
                    Color(0xFF222222),
                    size.height * 0.1f,
                    Offset(
                        size.width * x,
                        size.height * 0.5f
                    )
                )
                drawRect(
                    Color(0xFF222222),
                    topLeft = Offset(size.width * x - size.height * 0.12f, size.height * 0.6f),
                    size = Size(size.height * 0.24f, size.height * 0.4f)
                )
            }
        }
    }

    @Composable
    fun Brand() = GalleryColumn {
        ParcheULogo(size = WordmarkSize.Large)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ParcheULogo(size = WordmarkSize.Medium)
            ParcheULogo(size = WordmarkSize.Small)
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ParcheUAppIcon(size = 104.dp)
            ParcheUAppIcon(size = 72.dp)
            ParcheUAppIcon(size = 36.dp)
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TwinMascot(size = TwinMascotSize.L, talking = true, animationsEnabled = false)
            TwinMascot(
                size = TwinMascotSize.L,
                tone = TwinMascotTone.Friendship,
                talking = true,
                animationsEnabled = false
            )
            TwinMascot(size = TwinMascotSize.S, talking = true, animationsEnabled = false)
        }
    }

    @Composable
    private fun GalleryColumn(content: @Composable () -> Unit) {
        Column(
            Modifier.width(360.dp).background(ParcheUTheme.colors.bg).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Preview(name = "Parche Vivo · héroes · oscuro", widthDp = 360)
@Composable
private fun HeroesDarkPreview() {
    ParcheUTheme(darkTheme = true) { ParcheVivoGallery.Heroes() }
}

@Preview(name = "Parche Vivo · héroes · claro", widthDp = 360)
@Composable
private fun HeroesLightPreview() {
    ParcheUTheme(darkTheme = false) { ParcheVivoGallery.Heroes() }
}

@Preview(name = "Parche Vivo · lomos y tiles", widthDp = 360)
@Composable
private fun SpineCardsPreview() {
    ParcheUTheme(darkTheme = true) { ParcheVivoGallery.SpineCards() }
}

@Preview(name = "Parche Vivo · duotono", widthDp = 360)
@Composable
private fun DuotonesPreview() {
    ParcheUTheme(darkTheme = true) { ParcheVivoGallery.Duotones() }
}

@Preview(name = "Parche Vivo · marca", widthDp = 360)
@Composable
private fun BrandPreview() {
    ParcheUTheme(darkTheme = false) { ParcheVivoGallery.Brand() }
}
