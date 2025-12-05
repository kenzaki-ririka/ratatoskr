package com.neon10.ratatoskr.ui.overlay

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import com.neon10.ratatoskr.ai.AiProvider
import com.neon10.ratatoskr.data.ChatContextStore
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

data class ChatAction(val title: String, val text: String, val onClick: () -> Unit = {})

@Composable
fun ChatAssistPanel(actions: List<ChatAction> = emptyList()) {
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var panelActions by remember { mutableStateOf(actions) }
    var closingByAction by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ChatAction?>(null) }
    var boxW by remember { mutableStateOf(0f) }
    var boxH by remember { mutableStateOf(0f) }
    var boxX by remember { mutableStateOf(0f) }
    var boxY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bubbleSize = 56.dp

    val containerSize = bubbleSize
    val gap = 12.dp

    Box(
        modifier = Modifier
            .size(containerSize)
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                boxX = b.left
                boxY = b.top
                boxW = b.width
                boxH = b.height
            }
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .size(bubbleSize)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    if (expanded) {
                        closingByAction = false
                        expanded = false
                    } else if (!isLoading) {
                        isLoading = true
                    }
                }
                .align(Alignment.TopStart)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = if (expanded) "−" else "+",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (expanded) {
            val screenW = config.screenWidthDp.dp
            val screenH = config.screenHeightDp.dp
            val screenWpx = with(density) { screenW.toPx() }
            val screenHpx = with(density) { screenH.toPx() }
            val bubblePx = with(density) { bubbleSize.toPx() }
            val centerX = screenWpx / 2f
            val centerY = screenHpx / 2f
            val ballCX = boxX + bubblePx / 2f
            val ballCY = boxY + bubblePx / 2f
            val near = with(density) { 100.dp.toPx() }
            val shift = with(density) { (bubbleSize / 2 + 24.dp).toPx() }.toInt()
            val yAdjust = if (kotlin.math.abs(ballCX - centerX) < near && kotlin.math.abs(ballCY - centerY) < near) {
                if (ballCY <= centerY) shift else -shift
            } else 0
            Popup(alignment = Alignment.TopStart) {
                Box(
                    modifier = Modifier
                        .size(screenW, screenH)
                        .background(Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { closingByAction = false; pendingAction = null; expanded = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(0.dp)
                            .width(240.dp)
                            .offset(y = yAdjust.dp)
                    ) {
                        AnimatedVisibility(
                            visible = expanded,
                            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.95f),
                            exit = if (closingByAction) {
                                fadeOut(tween(280)) + scaleOut(tween(280), targetScale = 0.92f)
                            } else {
                                fadeOut(tween(160))
                            }
                        ) {
                            ChoicePanel(panelActions) { a ->
                                closingByAction = true
                                pendingAction = a
                                expanded = false
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(isLoading) {
            if (isLoading) {
                val ctxStr = ChatContextStore.getLast()
                val options = AiProvider.service.generateReplies(ctxStr, limit = 3)
                panelActions = options.map { opt ->
                    ChatAction(opt.title, opt.text) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("reply", opt.text))
                        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                    }
                }
                isLoading = false
                expanded = true
            }
        }
        LaunchedEffect(expanded, closingByAction, pendingAction) {
            if (!expanded && closingByAction && pendingAction != null) {
                delay(280)
                pendingAction?.onClick()
                pendingAction = null
                closingByAction = false
            }
        }
    }
}

@Composable
private fun ChoicePanel(actions: List<ChatAction>, onSelect: (ChatAction) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        actions.forEach { action ->
            ChoiceItem(action) { onSelect(action) }
        }
    }
}

@Composable
private fun ChoiceItem(action: ChatAction, onClick: () -> Unit = {}) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleChip(action.title, titleColor(action.title))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "建议",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = action.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TitleChip(title: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun titleColor(title: String): Color {
    return when (title) {
        "保守" -> MaterialTheme.colorScheme.primary
        "激进" -> MaterialTheme.colorScheme.error
        "出其不意" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}
