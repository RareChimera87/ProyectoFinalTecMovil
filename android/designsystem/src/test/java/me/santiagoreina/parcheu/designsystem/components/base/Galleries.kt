package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Galerías de componentes por grupo: una función por familia, cada una con los estados habilitado,
 * deshabilitado y seleccionado. Las usan las capturas Roborazzi y el test de semántica.
 */
object Galleries {
    @Composable
    fun Buttons() = GalleryCanvas {
        val icon = testIcon()
        for (size in ParcheUButtonSize.values()) {
            GalleryRow("Primary $size habilitado / deshabilitado") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Continuar", {}, size = size, icon = icon)
                    PrimaryButton("Continuar", {}, size = size, enabled = false)
                }
            }
            GalleryRow("Tonal $size habilitado / deshabilitado") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalButton("Ver más", {}, size = size)
                    TonalButton("Ver más", {}, size = size, enabled = false)
                }
            }
            GalleryRow("Outline $size habilitado / deshabilitado") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Cancelar", {}, size = size)
                    OutlineButton("Cancelar", {}, size = size, enabled = false)
                }
            }
            GalleryRow("Text $size habilitado / deshabilitado") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParcheUTextButton("Saltar", {}, size = size)
                    ParcheUTextButton("Saltar", {}, size = size, enabled = false)
                }
            }
        }
        GalleryRow("Primary ancho completo") {
            PrimaryButton("Continuar", {}, Modifier.fillMaxWidth())
        }
        GalleryRow("Google habilitado / deshabilitado") {
            GoogleSignInButton("Continuar con Google", {}, Modifier.fillMaxWidth())
            GoogleSignInButton("Continuar con Google", {}, Modifier.fillMaxWidth(), enabled = false)
        }
    }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    fun Chips() = GalleryCanvas {
        val colors = ParcheUTheme.colors
        val icon = testIcon()
        GalleryRow("ModeChip M / S") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeChip("Amistad", colors.friendship, icon = icon)
                ModeChip("Pareja", colors.couple, icon = icon)
                ModeChip("Estudio", colors.study, icon = icon)
                ModeChip("Amistad", colors.friendship, size = ModeChipSize.S, icon = icon)
                ModeChip("Pareja", colors.couple, size = ModeChipSize.S, icon = icon)
                ModeChip("Estudio", colors.study, size = ModeChipSize.S, icon = icon)
            }
        }
        GalleryRow("ModeFilterChip seleccionado / habilitado / deshabilitado") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeFilterChip("Todos", selected = true, onClick = {})
                ModeFilterChip("Amistad", selected = false, onClick = {})
                ModeFilterChip("Pareja", selected = false, onClick = {}, enabled = false)
            }
        }
        GalleryRow("SecondaryFilterChip habilitado / deshabilitado") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryFilterChip("Afinidad 70 %+", {})
                SecondaryFilterChip("Comunidad", {}, enabled = false)
            }
        }
        GalleryRow("InterestChip seleccionado / habilitado / deshabilitado") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InterestChip("Música", selected = true, onSelectedChange = {}, icon = icon)
                InterestChip("Cine", selected = false, onSelectedChange = {}, icon = icon)
                InterestChip("Anime", selected = false, onSelectedChange = {}, enabled = false)
            }
        }
        GalleryRow("PillChip neutra / afinidad") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("Aprende tus gustos")
                PillChip("Afinidad 82 %", variant = PillChipVariant.Affinity)
            }
        }
    }

    @Composable
    fun Segmented() = GalleryCanvas {
        val colors = ParcheUTheme.colors
        val two =
            listOf(ModeSegment("Amistad", colors.friendship), ModeSegment("Estudio", colors.study))
        val three = two.take(1) + ModeSegment("Pareja", colors.couple) + two.drop(1)
        GalleryRow("Por modo, 2 segmentos (44dp)") { ModeSegmentedControl(two, 0, {}) }
        GalleryRow("Por modo, 3 segmentos (48dp)") { ModeSegmentedControl(three, 1, {}) }
        GalleryRow("Por modo deshabilitado") { ModeSegmentedControl(three, 2, {}, enabled = false) }
        GalleryRow("Neutro") { SegmentedControl(listOf("Mi código", "Escanear"), 0, {}) }
        GalleryRow("Neutro deshabilitado") {
            SegmentedControl(listOf("Mi código", "Escanear"), 1, {}, enabled = false)
        }
    }

    @Composable
    fun Selection() = GalleryCanvas {
        GalleryRow("Switch on / off / deshabilitado on / deshabilitado off") {
            Row {
                ParcheUSwitch(true, {}, "Amistad activada")
                ParcheUSwitch(false, {}, "Pareja desactivada")
                ParcheUSwitch(true, {}, "Estudio bloqueado activado", enabled = false)
                ParcheUSwitch(false, {}, "Estudio bloqueado desactivado", enabled = false)
            }
        }
        GalleryRow("Checkbox marcada / vacía / deshabilitadas") {
            Row {
                ParcheUCheckbox(true, {}, "Acepto")
                ParcheUCheckbox(false, {}, "No acepto")
                ParcheUCheckbox(true, {}, "Acepto bloqueado", enabled = false)
                ParcheUCheckbox(false, {}, "No acepto bloqueado", enabled = false)
            }
        }
        GalleryRow("RadioRow seleccionada / sin seleccionar / deshabilitada") {
            ParcheURadioRow("Mensual", selected = true, onClick = {
            }, supportingText = "Se renueva cada mes")
            ParcheURadioRow("Anual", selected = false, onClick = {})
            ParcheURadioRow("Trimestral", selected = false, onClick = {}, enabled = false)
        }
    }

    @Composable
    fun Fields() = GalleryCanvas {
        GalleryRow("Campo vacío") {
            ParcheUTextField("", {}, "Fecha de nacimiento", placeholder = "DD/MM/AAAA")
        }
        GalleryRow("Campo con texto y ayuda") {
            ParcheUTextField("12/03/2004", {
            }, "Fecha de nacimiento", supportingText = "Debes ser mayor de 18")
        }
        GalleryRow("Campo con error") {
            ParcheUTextField("12/03/2015", {
            }, "Fecha de nacimiento", isError = true, supportingText = "Menor de 18")
        }
        GalleryRow("Campo deshabilitado") {
            ParcheUTextField("ABC123", {
            }, "Código", style = ParcheUTextFieldStyle.Code, enabled = false)
        }
        GalleryRow("Área de texto") {
            ParcheUTextField("", {}, "Cuéntanos", style = ParcheUTextFieldStyle.TextArea)
        }
    }

    @Composable
    fun Cards() = GalleryCanvas {
        SectionHeader("Tu parche", actionLabel = "Ver todo", onActionClick = {})
        ParcheUCard { Text("Tarjeta estándar", color = ParcheUTheme.colors.tx) }
        ParcheUCard(onClick = {}) { Text("Tarjeta clicable", color = ParcheUTheme.colors.tx) }
        ParcheUCard(variant = ParcheUCardVariant.Suggestion) {
            Text("Sugerencia", color = ParcheUTheme.colors.tx)
        }
        ParcheUCard(variant = ParcheUCardVariant.ActivePlan) {
            Text("Plan activo", color = ParcheUTheme.colors.tx)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard("24", "Conexiones", Modifier.padding(0.dp))
            StatCard("5", "Comunidades")
        }
    }

    @Composable
    fun Progress() = GalleryCanvas {
        GalleryRow("LinearProgress 0 / 60 / 100 %") {
            ParcheULinearProgress(0f, "Sin avance")
            ParcheULinearProgress(0.6f, "60 por ciento")
            ParcheULinearProgress(1f, "Completo")
        }
        GalleryRow("SegmentedQuota 0 / 3 / 5 de 5") {
            SegmentedQuota(0, 5, "0 de 5")
            SegmentedQuota(3, 5, "3 de 5")
            SegmentedQuota(5, 5, "5 de 5")
        }
        GalleryRow("ParcheU+ regular / grande") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParcheUPlusBadge()
                ParcheUPlusBadge(size = PlusBadgeSize.Large)
            }
        }
    }

    @Composable
    fun Bars() = GalleryCanvas {
        val icon = testIcon()
        ParcheUHomeTopBar(
            wordmark = { Text("ParcheU", color = ParcheUTheme.colors.tx) },
            actions = { ParcheUIconButton(icon, "Notificaciones", {}) }
        )
        ParcheURootTopBar("Conocer", actions = {
            ParcheUIconButton(icon, "Buscar", {})
            ParcheUIconButton(icon, "Filtrar", {})
        })
        ParcheUChildTopBar("Ajustes", icon, "Atrás", {
        }, action = { ParcheUTextButton("Guardar", {}, size = ParcheUButtonSize.Small) })
        ParcheUChatTopBar(
            name = "Camila",
            navigationIcon = icon,
            navigationContentDescription = "Atrás",
            onNavigationClick = {},
            avatar = { PillChip("C") },
            subtitle = "En línea",
            actions = { ParcheUIconButton(icon, "Más opciones", {}) }
        )
    }

    @Composable
    fun NavBars() = GalleryCanvas {
        val icon = testIcon()
        fun items(unread: Int) = listOf("home", "parche", "conocer", "chats", "perfil").map { key ->
            NavBarItem(
                key,
                key.replaceFirstChar { it.uppercase() },
                icon,
                unread = if (key ==
                    "chats"
                ) {
                    unread
                } else {
                    0
                }
            )
        }
        ParcheUNavBar(items(0), "home", {})
        ParcheUNavBar(items(3), "chats", {})
        ParcheUNavBar(items(120), "perfil", {})
    }
}
