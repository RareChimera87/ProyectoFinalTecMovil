package me.santiagoreina.parcheu.designsystem.components.compound

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.GalleryCanvas
import me.santiagoreina.parcheu.designsystem.components.base.GalleryRow
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUSwitch
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.base.StatCard
import me.santiagoreina.parcheu.designsystem.components.base.testIcon
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Galerías de los componentes compuestos para Roborazzi y para el test de semántica (E1.4). */
object CompoundGalleries {
    @Composable
    fun Sheets() = GalleryCanvas {
        GalleryRow("BottomSheet (contenido)") {
            ParcheUBottomSheetContent {
                Text("Detalle del nodo", color = ParcheUTheme.colors.tx)
                PrimaryButton("Ver perfil", {}, Modifier.fillMaxWidth())
            }
        }
        GalleryRow("ModalDialog (contenido) sobre scrim") {
            Box(Modifier.fillMaxWidth().height(200.dp).background(ParcheUTheme.colors.scrim)) {
                ParcheUModalDialogContent(Modifier.padding(top = 24.dp)) {
                    Text("¿Salir sin guardar?", color = ParcheUTheme.colors.tx)
                    PrimaryButton("Salir", {}, Modifier.fillMaxWidth())
                }
            }
        }
    }

    @Composable
    fun Locked() = GalleryCanvas {
        val icon = testIcon()
        GalleryRow("LockedOverlay lista") {
            LockedOverlay("Disponible con ParcheU+", "Ver ParcheU+", {}, icon)
        }
        GalleryRow("LockedOverlay grafo") {
            LockedOverlay("Disponible con ParcheU+", "Ver ParcheU+", {
            }, icon, placeholder = LockedPlaceholder.Graph)
        }
        GalleryRow("LockedFeatureBanner") {
            LockedFeatureBanner("Filtra por comunidad y afinidad", icon, onClick = {})
        }
    }

    @Composable
    fun Avatars() = GalleryCanvas {
        val icon = testIcon()
        GalleryRow("AvatarInitials por afinidad 30 / 60 / 75 / 90 / 100") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 75, 90, 100).forEach {
                    AvatarInitials("Camila Rojas", it, size = AvatarSizes.Lg)
                }
            }
        }
        GalleryRow("Tamaños 26 / 36 / 48 / 64 y anónimo") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarInitials("Ana Díaz", 80, size = AvatarSizes.Xs)
                AvatarInitials("Ana Díaz", 80, size = AvatarSizes.Md)
                AvatarInitials("Ana Díaz", 80, size = AvatarSizes.Lg)
                AvatarInitials("Ana Díaz", 80, size = AvatarSizes.Profile)
                AvatarInitials("", 0, size = AvatarSizes.Lg, anonymous = true, anonymousIcon = icon)
                AvatarInitials("", 0, size = AvatarSizes.Profile, anonymous = true)
            }
        }
        GalleryRow("StackedAvatars") {
            StackedAvatars(
                listOf(
                    AvatarSpec("Ana Díaz", 90),
                    AvatarSpec("Luis Pérez", 70),
                    AvatarSpec("", 0, anonymous = true),
                    AvatarSpec("Marta Gil", 55),
                    AvatarSpec("Pablo Ruiz", 60),
                ),
                size = AvatarSizes.List,
                borderColor = ParcheUTheme.colors.bg,
            )
        }
        GalleryRow("AffinityRing 112 (82 %) / 120 (35 %)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AffinityRing(82)
                AffinityRing(35, large = true)
            }
        }
    }

    @Composable
    fun Chat() = GalleryCanvas {
        val icon = testIcon()
        ChatBubble("Hola, ¿vamos por un café?", ChatBubbleKind.Other, timestamp = "10:12")
        ChatBubble("Dale, ¿a las cuatro?", ChatBubbleKind.Own, timestamp = "10:13")
        ChatBubble("Ambos disfrutan el senderismo", ChatBubbleKind.Twin)
        GalleryRow("ChatInputBar vacío / con texto") {
            ChatInputBar("", {}, {}, "Escribe un mensaje", icon)
            ChatInputBar("Nos vemos allá", {}, {}, "Escribe un mensaje", icon)
        }
    }

    @Composable
    fun Banners() = GalleryCanvas {
        val icon = testIcon()
        InfoBanner(
            "Tu gemelo aprende de tus respuestas.",
            icon = icon,
            actionLabel = "Entendido",
            onActionClick = {},
        )
        InfoNote("Puedes cambiar esto cuando quieras.", icon = icon)
        ConsentBanner(
            "Tratamos tus datos según la Ley 1581 de 2012.",
            linkLabel = "Política de privacidad",
            onLinkClick = {},
        )
        InfoListCard(
            "Qué verá tu pareja",
            listOf(
                InfoListItem("Tu nombre y tu foto", icon),
                InfoListItem("Tus intereses en común", icon),
                InfoListItem("Nada de tus chats", icon),
            ),
        )
    }

    @Composable
    fun States() = GalleryCanvas {
        GalleryRow("EmptyState") {
            EmptyState(
                "Aún no hay nadie",
                "Invita a un amigo para empezar a armar tu parche.",
                ctaLabel = "Invitar",
                onCtaClick = {},
            )
        }
        GalleryRow("ErrorState") {
            ErrorState(
                "No pudimos cargar",
                "Revisa tu conexión e inténtalo de nuevo.",
                retryLabel = "Reintentar",
                onRetryClick = {},
                secondaryLabel = "Volver",
                onSecondaryClick = {},
            )
        }
        GalleryRow("GraphSkeleton") {
            GraphSkeleton(Modifier.height(260.dp).fillMaxWidth(), animated = false)
        }
    }

    @Composable
    fun Rows() = GalleryCanvas {
        val icon = testIcon()
        var on by remember { mutableStateOf(true) }
        SettingsRow("Notificaciones", icon, {}, subtitle = "Mensajes y avisos") {
            ParcheUSwitch(checked = on, onCheckedChange = {
                on = it
            }, contentDescription = "Notificaciones")
        }
        SettingsRow("Reportar usuario", icon, {}, destructive = true)
        SettingsRow("Borrar cuenta", icon, {}, destructive = true, enabled = false)
        NotificationRow("Camila quiere conectar", "Tienen 87 % de afinidad.", "Hace 5 min", true, {
        }, {
            IconCircle(icon)
        })
        NotificationRow("Tu parche creció", "Dos personas nuevas.", "Ayer", false, {}, {
            IconCircle(icon)
        })
    }

    @Composable
    fun Footers() = GalleryCanvas {
        DecisionFooter("Ahora no", {
        }, "Conectar", {}, note = "Solo verán lo que tú decidas compartir.")
        PageIndicator(3, 1)
        LoadingDots(animated = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("12", "Conexiones", Modifier.weight(1f))
            StatCard("87 %", "Afinidad", Modifier.weight(1f))
        }
    }

    @Composable
    fun Celebration() = GalleryCanvas {
        GalleryRow("CelebrationBackground") {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                CelebrationBackground {
                    Text(
                        "¡Conectaron!",
                        color = ParcheUTheme.colors.tx,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    @Composable
    fun Charts() = GalleryCanvas {
        val icon = testIcon()
        GalleryRow("QRCard") {
            QRCard(sampleQr(), "Santiago R.", "Código QR de Santiago", logo = icon)
        }
        GalleryRow("LineChart") {
            LineChart(
                points = listOf(42f, 48f, 55f, 53f, 64f, 72f, 80f),
                contentDescription = "Precisión del gemelo: de 42 % a 80 %",
                startLabel = "Sem 1",
                endLabel = "Sem 7",
            )
        }
    }
}

/** QR sintético 25 x 25 (tres marcadores de esquina y módulos pseudoaleatorios): solo para capturas. */
fun sampleQr(size: Int = 25): QrMatrix {
    val modules = BooleanArray(size * size)
    fun finder(r0: Int, c0: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val edge = r == 0 || r == 6 || c == 0 || c == 6
                val core = r in 2..4 && c in 2..4
                modules[(r0 + r) * size + (c0 + c)] = edge || core
            }
        }
    }
    var seed = 7
    for (i in modules.indices) {
        seed = (seed * 1103515245 + 12345) and 0x7fffffff
        modules[i] = (seed shr 16) % 2 == 0
    }
    finder(0, 0)
    finder(0, size - 7)
    finder(size - 7, 0)
    return QrMatrix(size, modules)
}
