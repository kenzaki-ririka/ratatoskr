package com.neon10.ratatoskr.ui.overlay

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import kotlin.math.abs
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import com.neon10.ratatoskr.ai.AiProvider
import com.neon10.ratatoskr.data.ChatContextStore
import com.neon10.ratatoskr.data.AiSettingsStore
import com.neon10.ratatoskr.data.ChatMessageCollector
import com.neon10.ratatoskr.service.ChatAccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

data class ChatAction(val title: String, val text: String, val onClick: () -> Unit = {})

@Composable
fun ChatAssistPanel(actions: List<ChatAction> = emptyList()) {
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isScrolling by remember { mutableStateOf(false) }  // 长按滚动状态
    var panelActions by remember { mutableStateOf(actions) }
    var closingByAction by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ChatAction?>(null) }
    var boxW by remember { mutableStateOf(0f) }
    var boxH by remember { mutableStateOf(0f) }
    var boxX by remember { mutableStateOf(0f) }
    var boxY by remember { mutableStateOf(0f) }
    
    // New state for collected messages
    var showCollectedMessages by remember { mutableStateOf(false) }
    var collectedResult by remember { mutableStateOf<ChatMessageCollector.CollectionResult?>(null) }
    var collectCompleted by remember { mutableStateOf(false) }  // 标记是否已采集完成（长按滚动后）
    
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bubbleSize = 56.dp
    
    // 自定义 touch slop（5dp），手指移动超过此距离则判定为拖动
    val customTouchSlop = with(density) { 5.dp.toPx() }

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
                .background(
                    if (isScrolling) MaterialTheme.colorScheme.tertiary 
                    else MaterialTheme.colorScheme.primary
                )
                .pointerInput(expanded, showCollectedMessages, isLoading, isScrolling) {
                    val longPressTimeout = 300L
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startPosition = down.position
                        var longPressTriggered = false
                        var dragDetected = false
                        
                        try {
                            // 等待长按超时或手指抬起
                            withTimeout(longPressTimeout) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentPosition = event.changes.firstOrNull()?.position ?: break
                                    
                                    // 检查是否移动超过 5dp（自定义 touch slop）
                                    val dx = abs(currentPosition.x - startPosition.x)
                                    val dy = abs(currentPosition.y - startPosition.y)
                                    if (dx > customTouchSlop || dy > customTouchSlop) {
                                        dragDetected = true
                                        break  // 判定为拖动，退出等待
                                    }
                                    
                                    // 检查是否抬起
                                    if (event.changes.all { !it.pressed }) {
                                        break
                                    }
                                }
                            }
                        } catch (e: PointerEventTimeoutCancellationException) {
                            // 长按超时，且没有检测到拖动 → 触发长按滚动
                            if (!dragDetected && !isLoading && !isScrolling) {
                                val service = ChatAccessibilityService.instance
                                if (service == null) {
                                    Toast.makeText(ctx, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                                } else {
                                    longPressTriggered = true
                                    isScrolling = true
                                    
                                    // 累积采集的消息
                                    var accumulatedMessages = mutableListOf<ChatMessageCollector.ChatMessage>()
                                    var lastAppName: String? = null
                                    
                                    // 先采集当前屏幕
                                    val initialResult = ChatMessageCollector.collect()
                                    accumulatedMessages.addAll(initialResult.messages)
                                    lastAppName = initialResult.appName
                                    
                                    // 用于控制滚动循环的标志（AtomicBoolean 保证线程安全）
                                    val keepScrolling = java.util.concurrent.atomic.AtomicBoolean(true)
                                    
                                    // 启动持续滚动协程
                                    val scrollJob = CoroutineScope(Dispatchers.Main).launch {
                                        while (keepScrolling.get()) {
                                            // 滚动
                                            val success = service.scrollUpSuspend()
                                            if (!success) break
                                            
                                            // 等待滚动动画完成
                                            delay(400)
                                            
                                            if (!keepScrolling.get()) break
                                            
                                            // 采集新内容并合并
                                            val newResult = ChatMessageCollector.collect()
                                            accumulatedMessages = ChatMessageCollector.mergeMessages(
                                                accumulatedMessages, 
                                                newResult.messages
                                            ).toMutableList()
                                            lastAppName = newResult.appName ?: lastAppName
                                            
                                            // 滚动间隔
                                            delay(100)
                                        }
                                    }
                                    
                                    // 等待手指抬起
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.all { !it.pressed }) {
                                            // 手指抬起，停止滚动
                                            keepScrolling.set(false)
                                            isScrolling = false
                                            scrollJob.cancel()
                                            
                                            // 用累积的消息更新结果
                                            val finalContext = ChatMessageCollector.buildContext(accumulatedMessages)
                                            ChatContextStore.setLast(finalContext)
                                            
                                            collectedResult = ChatMessageCollector.CollectionResult(
                                                messages = accumulatedMessages.takeLast(20),
                                                rawContext = finalContext,
                                                appName = lastAppName,
                                                debugInfo = "累积采集 ${accumulatedMessages.size} 条消息"
                                            )
                                            showCollectedMessages = true
                                            collectCompleted = true  // 标记已采集完成，防止 LaunchedEffect 重新采集
                                            isLoading = true
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 如果不是长按触发，等待手指抬起处理点击
                        if (!longPressTriggered) {
                            val up = waitForUpOrCancellation()
                            if (up != null && !dragDetected) {
                                if (expanded || showCollectedMessages) {
                                    closingByAction = false
                                    expanded = false
                                    showCollectedMessages = false
                                    collectedResult = null
                                } else if (!isLoading && !isScrolling) {
                                    isLoading = true
                                }
                            }
                        }
                    }
                }
                .align(Alignment.TopStart)
        ) {
            if (isLoading || isScrolling) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = if (expanded || showCollectedMessages) "−" else "+",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (expanded || showCollectedMessages) {
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
                        ) { 
                            closingByAction = false
                            pendingAction = null
                            expanded = false
                            showCollectedMessages = false
                            collectedResult = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(0.dp)
                            .width(280.dp)
                            .fillMaxHeight(1f)
                            .offset(y = yAdjust.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Collected Messages Panel (appears above suggestions)
                        AnimatedVisibility(
                            visible = showCollectedMessages && collectedResult != null,
                            modifier = Modifier.weight(1f, fill = false),
                            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it }
                        ) {
                            collectedResult?.let { result ->
                                CollectedMessagesPanel(
                                    result = result,
                                    onClose = {
                                        showCollectedMessages = false
                                    },
                                    onCopy = {
                                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("context", result.rawContext))
                                        Toast.makeText(ctx, "已复制上下文", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                        
                        // AI Suggestions Panel
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
        
        // Collect messages and generate AI suggestions
        LaunchedEffect(isLoading) {
            if (isLoading) {
                try {
                    // 只有没有采集完成时才重新采集（长按滚动后已经采集了）
                    if (!collectCompleted) {
                        val result = withContext(Dispatchers.Default) {
                            ChatMessageCollector.collect()
                        }
                        collectedResult = result
                        showCollectedMessages = true
                    }
                    
                    // 生成 AI 建议
                    val ctxStr = ChatContextStore.getLast()
                    val options = AiProvider.service.generateReplies(ctxStr, limit = AiSettingsStore.limit)
                    panelActions = options.map { opt ->
                        ChatAction(opt.title, opt.text) {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("reply", opt.text))
                            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                    expanded = true
                } catch (e: Exception) {
                    Toast.makeText(ctx, "生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                    collectCompleted = false  // 重置标志
                }
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
private fun CollectedMessagesPanel(
    result: ChatMessageCollector.CollectionResult,
    onClose: () -> Unit,
    onCopy: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "采集到的消息",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (result.appName != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = getAppDisplayName(result.appName),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Messages content
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(scrollState)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!ChatMessageCollector.isAccessibilityEnabled()) {
                    // 无障碍服务未启用
                    Text(
                        text = "⚠️ 请先开启无障碍服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else if (result.messages.isNotEmpty()) {
                    // 结构化消息模式：显示消息列表
                    result.messages.forEach { msg ->
                        MessageItem(msg)
                    }
                } else if (result.rawContext.isNotBlank()) {
                    // 非结构化模式：直接显示 rawContext（与发送给 API 的内容一致）
                    Text(
                        text = result.rawContext,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    // 真正没有采集到内容
                    Text(
                        text = "未采集到消息，请确保在聊天界面使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Stats
            Text(
                text = "共 ${result.messages.size} 条消息，${result.rawContext.length} 字符",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MessageItem(msg: ChatMessageCollector.ChatMessage) {
    val senderLabel = if (msg.isFromSelf) "[我]" else "[${msg.sender ?: "对方"}]"
    Column {
        Text(
            text = senderLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (msg.isFromSelf) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.secondary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        Text(
            text = msg.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getAppDisplayName(packageName: String?): String {
    return when (packageName) {
        "com.tencent.mm" -> "微信"
        "com.tencent.mobileqq" -> "QQ"
        else -> packageName?.substringAfterLast('.') ?: "未知"
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

