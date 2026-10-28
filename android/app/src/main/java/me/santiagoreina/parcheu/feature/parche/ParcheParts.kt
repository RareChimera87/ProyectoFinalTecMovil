package me.santiagoreina.parcheu.feature.parche

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.EdgeState
import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.NodeDto
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.ModeChip
import me.santiagoreina.parcheu.designsystem.components.base.ModeChipSize
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUPlusBadge
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextButton
import me.santiagoreina.parcheu.designsystem.components.base.PillChip
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.TonalButton
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.designsystem.theme.affinityAlpha
import me.santiagoreina.parcheu.designsystem.theme.avatarColors
import me.santiagoreina.parcheu.feature.safety.ReportOrBlockAction

const val PARCHE_SHEET_TAG = "parche_sheet"
const val PARCHE_BANNER_TAG = "parche_banner"
const val PARCHE_SKELETON_TAG = "parche_skeleton"
const val PARCHE_DEMO_TAG = "parche_demo"

/** Insignia de D2 cuando el nodo es un perfil de demostración (ADR-0027). */
const val PARCHE_DEMO_BADGE = "Perfil de demostración"

/** Etiqueta de un modo para la UI ("Amistad", "Pareja", "Estudio"). */
fun Mode.label(): String = toGraphMode().label

fun Mode.icon(): ParcheIcon = when (this) {
    Mode.AMISTAD -> ParcheIcon.Friends
    Mode.PAREJA -> ParcheIcon.Heart
    Mode.ESTUDIO -> ParcheIcon.School
}

/** "hoy", "ayer" o "hace N días" entre [createdAt] y [now]. */
fun relativeDays(createdAt: Instant, now: Instant): String {
    val days = (now - createdAt).inWholeDays.coerceAtLeast(0)
    return when (days) {
        0L -> "hoy"
        1L -> "ayer"
        else -> "hace $days días"
    }
}

/** Texto de error de D5 / de la hoja según el error. */
fun AppError.parcheMessage(): String = when (this) {
    AppError.Network -> "Revisa tu conexión e intenta otra vez. Tus conexiones están a salvo."
    AppError.PlusRequired -> "Esta acción es parte de ParcheU+."
    is AppError.QuotaExceeded -> "Ya usaste tus simulaciones disponibles por ahora."
    AppError.Blocked, AppError.Forbidden -> "No puedes hacer esto con esta persona."
    else -> "Algo salió mal. Intenta otra vez en un momento."
}

@Composable
fun NodeAvatar(node: NodeDto, size: Dp, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    if (node.anonymous) {
        Box(
            modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.s2)
                .dashedBorder(colors.prt, size / 2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = parcheIconPainter(ParcheIcon.Person),
                contentDescription = null,
                tint = colors.mu,
                modifier = Modifier.size(size / 2)
            )
        }
    } else {
        val avatar = avatarColors(affinityAlpha(node.affinity), colors.isDark)
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(avatar.fill),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = node.initials,
                style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = (size.value * 0.37f).sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = avatar.content
            )
        }
    }
}

private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color, radius: Dp) =
    drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style =
            Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            )
        )
    }

/** Leyenda de aristas del grafo: sólida, punteada y tenue (inventario §3, GraphEdgeLegend). */
@Composable
fun GraphEdgeLegend(modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Conectados, simularon y pendiente"
        },
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
    ) {
        listOf("Conectados" to 0, "Simularon" to 1, "Pendiente" to 2).forEach { (label, kind) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 22.dp, height = 4.dp)) {
                    val y = size.height / 2
                    when (kind) {
                        0 -> drawLine(
                            colors.tx,
                            Offset(0f, y),
                            Offset(size.width, y),
                            2.5.dp.toPx()
                        )
                        1 -> drawLine(
                            colors.tx,
                            Offset(0f, y),
                            Offset(size.width, y),
                            2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                            )
                        )
                        else -> drawLine(
                            colors.tx.copy(alpha = 0.35f),
                            Offset(0f, y),
                            Offset(size.width, y),
                            1.5.dp.toPx()
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.mu)
            }
        }
    }
}

/** Etiqueta de la entrada a amigos de amigos (D6) para ParcheU+. */
const val PARCHE_FOF_ENTRY_TAG = "parche_fof_entry"

/** Entrada de ParcheU+ a D6 (E6.3): misma forma que el banner, sin candado. */
@Composable
fun FriendsOfFriendsEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(PARCHE_FOF_ENTRY_TAG)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.s1)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = parcheIconPainter(ParcheIcon.Friends),
            contentDescription = null,
            tint = colors.prt,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Amigos de amigos: personas afines a 2 pasos",
            style = MaterialTheme.typography.bodySmall,
            color = colors.tx,
            modifier = Modifier.weight(1f)
        )
        ParcheUPlusBadge()
    }
}

/** Banner de bloqueo para gratis: candado, texto y la insignia ParcheU+; lleva a D7 (y de ahí a G1). */
@Composable
fun LockedFeatureBanner(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(PARCHE_BANNER_TAG)
            .clip(RoundedCornerShape(16.dp))
            .dashedBorderBox(colors.ln)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = parcheIconPainter(ParcheIcon.Lock),
            contentDescription = null,
            tint = colors.mu,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.tx,
            modifier = Modifier.weight(1f)
        )
        ParcheUPlusBadge()
    }
}

private fun Modifier.dashedBorderBox(color: androidx.compose.ui.graphics.Color) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(16.dp.toPx()),
        style =
        Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
    )
}

/** D4: esqueleto del grafo, animado con un pulso suave. */
@Composable
fun GraphSkeleton(modifier: Modifier = Modifier, animate: Boolean = true) {
    val colors = ParcheUTheme.colors
    val pulse =
        if (animate) {
            val transition = rememberInfiniteTransition(label = "parche-skeleton")
            val value by
                transition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "pulse"
                )
            value
        } else {
            1f
        }
    Column(
        modifier = modifier.fillMaxSize().testTag(PARCHE_SKELETON_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(300.dp).alpha(pulse)) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(0f, 50f, 100f).forEach { inset ->
                    val d = (300f - 2 * inset).dp.toPx()
                    drawCircle(
                        colors.s1,
                        radius = d / 2 - 5.dp.toPx(),
                        center = center,
                        style = Stroke(10.dp.toPx())
                    )
                }
                drawCircle(colors.s2, 24.dp.toPx(), center)
                listOf(
                    Offset(55f, 75f),
                    Offset(235f, 105f),
                    Offset(205f, 245f),
                    Offset(85f, 225f)
                ).forEach {
                    drawCircle(colors.s2, 15.dp.toPx(), Offset(it.x.dp.toPx(), it.y.dp.toPx()))
                }
            }
        }
        Text(
            "Armando tu parche…",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
            color = colors.mu
        )
    }
}

/** D3: primer uso, sin conexiones todavía. */
@Composable
fun ParcheEmptyState(
    onAddFriends: () -> Unit,
    onRandomMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                listOf(0f, 44f, 84f).forEach { inset ->
                    drawCircle(
                        colors.ln,
                        radius = (240f - 2 * inset).dp.toPx() / 2,
                        center = center,
                        style =
                        Stroke(
                            1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 5.dp.toPx())
                            )
                        )
                    )
                }
                drawCircle(colors.primary, 26.dp.toPx(), center)
            }
        }
        Text(
            "Tu parche está por armarse",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            "Agrega a tus amigos o haz tu primera simulación y aquí vas a ver tu red.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        PrimaryButton(
            text = "Agregar amigos",
            onClick = onAddFriends,
            modifier = Modifier.fillMaxWidth(),
            icon = parcheIconPainter(ParcheIcon.PersonAdd)
        )
        ParcheUTextButton(
            text = "Probar match al azar",
            onClick = onRandomMatch
        )
    }
}

/** D5: falló la carga. */
@Composable
fun ParcheErrorState(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ParcheUTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(colors.s2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = parcheIconPainter(ParcheIcon.CloudOff),
                contentDescription = null,
                tint = colors.mu,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            "No pudimos cargar tu parche",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.tx,
            textAlign = TextAlign.Center
        )
        Text(
            error.parcheMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mu,
            textAlign = TextAlign.Center
        )
        PrimaryButton(
            text = "Reintentar",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            icon = parcheIconPainter(ParcheIcon.Replay)
        )
    }
}

/** Hoja D2 con scrim: detalle del nodo y sus acciones según el estado de la conexión. */
@Composable
fun NodeDetailSheet(
    sheet: NodeSheet,
    now: Instant,
    onDismiss: () -> Unit,
    onSimulateAgain: () -> Unit,
    onChat: () -> Unit,
    onViewResult: () -> Unit,
    modifier: Modifier = Modifier,
    onReportOrBlock: () -> Unit = {}
) {
    val colors = ParcheUTheme.colors
    val node = sheet.node
    val last = sheet.detail?.lastSimulation
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClickLabel = "Cerrar", onClick = onDismiss)
        )
        Column(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag(PARCHE_SHEET_TAG)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.s1)
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.ln)
                )
                // E8.7: reportar o bloquear a esta persona, siempre accesible y gratis.
                ReportOrBlockAction(
                    onClick = onReportOrBlock,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NodeAvatar(node, 60.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (node.anonymous) "Alguien" else node.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.tx,
                        maxLines = 1
                    )
                    ModeChip(
                        label = node.mode.label(),
                        mode =
                        when (node.mode) {
                            Mode.AMISTAD -> colors.friendship
                            Mode.PAREJA -> colors.couple
                            Mode.ESTUDIO -> colors.study
                        },
                        size = ModeChipSize.S,
                        icon = parcheIconPainter(node.mode.icon())
                    )
                    if (node.isDemo) PillChip(PARCHE_DEMO_BADGE, Modifier.testTag(PARCHE_DEMO_TAG))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${node.affinity} %",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.prt
                    )
                    Text(
                        "afinidad",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.mu
                    )
                }
            }
            SheetStatusRow(
                node.state,
                last?.let {
                    relativeDays(it.createdAt, now)
                },
                sheet.loadingDetail
            )
            sheet.actionError?.let {
                Text(
                    it.parcheMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (node.state) {
                    EdgeState.CONNECTED -> {
                        TonalButton(
                            "Simular de nuevo",
                            onSimulateAgain,
                            Modifier.fillMaxWidth(),
                            enabled = !sheet.starting,
                            icon = parcheIconPainter(ParcheIcon.Replay)
                        )
                        // Sin chatId (la API aún no lo manda) no hay a dónde ir: se oculta.
                        if (sheet.detail?.chatId != null) {
                            PrimaryButton(
                                "Chatear",
                                onChat,
                                Modifier.fillMaxWidth(),
                                icon = parcheIconPainter(ParcheIcon.Chat)
                            )
                        }
                    }
                    EdgeState.SIMULATED -> {
                        TonalButton(
                            "Simular de nuevo",
                            onSimulateAgain,
                            Modifier.fillMaxWidth(),
                            enabled = !sheet.starting,
                            icon = parcheIconPainter(ParcheIcon.Replay)
                        )
                        if (last != null) {
                            PrimaryButton("Ver resultado", onViewResult, Modifier.fillMaxWidth())
                        }
                    }
                    EdgeState.PENDING ->
                        if (last != null) {
                            PrimaryButton("Ver resultado", onViewResult, Modifier.fillMaxWidth())
                        }
                }
            }
        }
    }
}

@Composable
private fun SheetStatusRow(state: EdgeState, lastSimulation: String?, loading: Boolean) {
    val colors = ParcheUTheme.colors
    val (title, subtitle) =
        when {
            lastSimulation != null -> "Última simulación · $lastSimulation" to state.sheetNote()
            loading -> "Cargando detalle…" to state.sheetNote()
            else -> state.sheetNote() to null
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.s2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = parcheIconPainter(ParcheIcon.Forum),
            contentDescription = null,
            tint = colors.mu,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.tx
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.mu
                )
            }
        }
    }
}

fun EdgeState.sheetNote(): String = when (this) {
    EdgeState.CONNECTED -> "Ya están conectados"
    EdgeState.SIMULATED -> "Simularon, falta conectar"
    EdgeState.PENDING -> "Esperando la decisión de ambos"
}
