package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Asa de 36 x 4 del BottomSheet (inventario §2, NodeDetailBottomSheet). */
@Composable
internal fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(36.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(ParcheUTheme.colors.ln)
            // Decorativa: el lector de pantalla no debe anunciarla.
            .clearAndSetSemantics { }
    )
}

/**
 * Superficie del BottomSheet: s1, radio 28 28 0 0, asa 36 x 4 y relleno 10 / 20 / 30. Es el contenido que
 * [ParcheUBottomSheet] muestra dentro de la ventana modal; se expone aparte para poder componerla y
 * capturarla sin ventana.
 */
@Composable
fun ParcheUBottomSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ParcheUTheme.shapes.bottomSheet)
            .background(ParcheUTheme.colors.s1, ParcheUTheme.shapes.bottomSheet)
            .padding(PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 30.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetHandle()
        content()
    }
}

/**
 * BottomSheet modal con scrim `rgba(5,3,12,.62)` en oscuro. Se cierra con el scrim, el gesto de atrás o
 * al arrastrarlo hacia abajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcheUBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ParcheUTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        shape = ParcheUTheme.shapes.bottomSheet,
        containerColor = colors.s1,
        contentColor = colors.tx,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 2.dp)) { SheetHandle() }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 30.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Tarjeta del diálogo: s1, radio 28 y margen lateral de 24dp. */
@Composable
fun ParcheUModalDialogContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(ParcheURadius.HeroLarge)
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(ParcheUTheme.colors.s1, shape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

/**
 * Diálogo modal con scrim propio (pantalla completa, `rgba(5,3,12,.62)`) y tarjeta s1 de radio 28.
 * Tocar el scrim lo cierra cuando [dismissOnScrimClick] es verdadero.
 */
@Composable
fun ParcheUModalDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimClick: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ParcheUTheme.colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissOnScrimClick,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            // El clic sobre la tarjeta no debe llegar al scrim.
            ParcheUModalDialogContent(
                modifier = modifier.pointerInput(Unit) { detectTapGestures { } },
                content = content
            )
        }
    }
}
