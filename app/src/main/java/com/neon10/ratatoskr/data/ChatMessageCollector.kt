package com.neon10.ratatoskr.data

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.neon10.ratatoskr.service.ChatAccessibilityService

/**
 * Collects chat messages from QQ/WeChat using Assists accessibility API.
 * Only collects when explicitly triggered by user clicking the floating button.
 */
object ChatMessageCollector {
    private const val TAG = "ChatCollector"

    data class ChatMessage(
        val sender: String?,
        val content: String,
        val isFromSelf: Boolean = false
    )

    data class CollectionResult(
        val messages: List<ChatMessage>,
        val rawContext: String,
        val appName: String?,
        val debugInfo: String = ""  // 调试信息
    )

    /**
     * Collect messages from the current screen.
     * Should be called when user clicks the floating button.
     */
    fun collect(): CollectionResult {
        val messages = mutableListOf<ChatMessage>()
        val textParts = mutableListOf<String>()
        var appName: String? = null
        val debugLog = StringBuilder()

        try {
            // Get the accessibility service instance
            val service = ChatAccessibilityService.instance
            debugLog.appendLine("Service instance: ${service != null}")
            // Log.d(TAG, "Service instance: ${service != null}")
            
            if (service == null) {
                debugLog.appendLine("Service is null, cannot collect")
                return CollectionResult(emptyList(), "", null, debugLog.toString())
            }
            
            // Try method 1: Get current active window's root node
            var rootNode = service.rootInActiveWindow
            appName = rootNode?.packageName?.toString()
            debugLog.appendLine("Method 1 - Active Window:")
            debugLog.appendLine("  Package: $appName")
            debugLog.appendLine("  Root node: ${rootNode != null}")
            // Log.d(TAG, "Active Window - Package: $appName, Root node: ${rootNode != null}")
            
            // Check if root node is empty (WeChat protection)
            val isEmptyRoot = rootNode != null && rootNode.childCount == 0 && rootNode.className == null
            if (isEmptyRoot) {
                debugLog.appendLine("  Root is empty (possible protection), trying windows...")
                // Log.d(TAG, "Root is empty, trying getWindows()")
                rootNode?.recycle()
                rootNode = null
            }
            
            // Try method 2: Get all windows and find the right one
            if (rootNode == null || isEmptyRoot) {
                debugLog.appendLine("Method 2 - Windows API:")
                val windows = service.windows
                debugLog.appendLine("  Total windows: ${windows?.size ?: 0}")
                // Log.d(TAG, "Total windows: ${windows?.size ?: 0}")
                
                windows?.forEachIndexed { index, window ->
                    val windowRoot = window.root
                    val pkg = windowRoot?.packageName?.toString()
                    val childCount = windowRoot?.childCount ?: 0
                    val className = windowRoot?.className?.toString()
                    debugLog.appendLine("  Window[$index]: pkg=$pkg, class=$className, children=$childCount")
                    // Log.d(TAG, "Window[$index]: pkg=$pkg, class=$className, children=$childCount")
                    
                    // Use this window if it has content
                    if (windowRoot != null && childCount > 0) {
                        if (rootNode != null) rootNode.recycle()
                        rootNode = windowRoot
                        appName = pkg
                        debugLog.appendLine("  → Using this window")
                    } else {
                        windowRoot?.recycle()
                    }
                }
            }

            if (rootNode != null) {
                // Log root node info
                // Log.d(TAG, "Final Root class: ${rootNode.className}, childCount: ${rootNode.childCount}")
                debugLog.appendLine("Final root - class: ${rootNode.className}, children: ${rootNode.childCount}")
                
                // 打印节点树（所有应用都打印，方便后续解析）
                //Log.d(TAG, "========== NODE TREE ($appName) ==========")
                //dumpNodeStructure(rootNode, 0)
                //Log.d(rootNode)
                //Log.d(TAG, "========== END NODE TREE ==========")
                
                // TIM/QQ 专用：使用结构化解析（如果启用）
                val isTIMOrQQ = appName == "com.tencent.tim" || appName == "com.tencent.mobileqq"
                if (isTIMOrQQ && AiSettingsStore.enableStructuredParsing) {
                    // Log.d(TAG, "========== TIM/QQ STRUCTURED COLLECTION ==========")
                    val timMessages = collectTIMMessages(rootNode)
                    messages.addAll(timMessages)
                    
                    // 生成结构化上下文
                    timMessages.forEach { msg ->
                        val prefix = if (msg.isFromSelf) "[我]" else "[${msg.sender}]"
                        textParts.add("$prefix: ${msg.content}")
                    }
                    // Log.d(TAG, "TIM messages collected: ${timMessages.size}")
                    // Log.d(TAG, "========== END TIM COLLECTION ==========")
                } else {
                    // 非结构化模式或非 TIM/QQ：只收集纯文本，不创建结构化消息
                    collectTextsFromNode(rootNode, textParts, 0, debugLog)
                }
                rootNode.recycle()
            } else {
                debugLog.appendLine("No valid root node found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting: ${e.message}", e)
            debugLog.appendLine("Error: ${e.message}")
            e.printStackTrace()
        }

        debugLog.appendLine("Total texts found: ${textParts.size}")
        debugLog.appendLine("Total messages: ${messages.size}")
        // Log.d(TAG, "Total texts: ${textParts.size}, messages: ${messages.size}")

        // Clean and deduplicate
        val cleanedTexts = textParts
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(50) // Keep last 50 messages for context

        // Build context string (max 2000 chars as per design)
        val rawContext = cleanedTexts.joinToString("\n").take(2000)

        // Store to ChatContextStore for AI processing
        ChatContextStore.setLast(rawContext)

        return CollectionResult(
            messages = messages.takeLast(20), // Keep last 20 for display
            rawContext = rawContext,
            appName = appName,
            debugInfo = debugLog.toString()
        )
    }
    
    /**
     * TIM/QQ 专用消息收集器
     * 根据 viewId 识别发送者(9w)和消息内容(9u)
     */
    private fun collectTIMMessages(rootNode: AccessibilityNodeInfo): List<ChatMessage> {
        data class TextNode(
            val y: Int, 
            val x: Int, 
            val viewId: String, 
            val text: String,
            val parentViewId: String = "",  // 父容器 viewId
            val parentClass: String = ""    // 父容器类名
        )
        
        val textNodes = mutableListOf<TextNode>()
        
        // 递归收集所有文本节点
        fun collectNodes(node: AccessibilityNodeInfo, parentId: String = "", parentClass: String = "") {
            val text = node.text?.toString()
            val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""
            val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            val className = node.className?.toString()?.substringAfterLast(".") ?: ""
            
            if (!text.isNullOrBlank()) {
                textNodes.add(TextNode(bounds.top, bounds.left, viewId, text, parentId, parentClass))
                // 打印更多信息帮助分析
                if (viewId == "9u") {
                    // Log.d(TAG, "MSG NODE: x=${bounds.left} parentId=$parentId parentClass=$parentClass text=${text.take(20)}")
                }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectNodes(child, viewId.ifEmpty { parentId }, className.ifEmpty { parentClass })
                    child.recycle()
                }
            }
        }
        collectNodes(rootNode)
        
        // 按 y 坐标排序
        val sorted = textNodes.sortedBy { it.y }
        
        // 检测是否是群聊（群聊有发送者昵称 9w，私聊没有）
        val hasNicknames = sorted.any { it.viewId == "9w" }
        val isGroupChat = hasNicknames
        
        // 获取私聊对方备注名（从标题栏 si5 获取）
        val contactName = sorted.find { it.viewId == "si5" }?.text ?: "对方"
        
        // 配对发送者和消息
        val messages = mutableListOf<ChatMessage>()
        var lastSender: String? = null      // 上一条消息的发送者（用于连续消息）
        var lastIsFromSelf = false          // 上一条消息是否是自己发的
        
        // 使用屏幕宽度作为阈值
        val screenWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels
        val midX = screenWidth / 7
        
        for (node in sorted) {
            when {
                // 发送者昵称 (viewId 包含 9w) - 群聊才有
                node.viewId == "9w" -> {
                    lastSender = node.text
                    lastIsFromSelf = node.x > midX
                }
                // 消息内容 (viewId 包含 9u)
                node.viewId == "9u" -> {
                    val msgIsFromSelf = node.x > midX
                    
                    val sender: String
                    val isFromSelf: Boolean
                    
                    if (isGroupChat) {
                        // 群聊逻辑
                        if (msgIsFromSelf) {
                            sender = "我"
                            isFromSelf = true
                        } else {
                            // 使用当前发送者，如果没有则沿用上一个，再没有显示群友
                            sender = lastSender ?: "群友"
                            isFromSelf = false
                        }
                    } else {
                        // 私聊逻辑
                        if (msgIsFromSelf) {
                            sender = "我"
                            isFromSelf = true
                        } else {
                            sender = contactName  // 使用对方备注名
                            isFromSelf = false
                        }
                    }
                    
                    messages.add(ChatMessage(
                        sender = sender,
                        content = node.text,
                        isFromSelf = isFromSelf
                    ))
                    
                    // 群聊：不重置 lastSender，连续消息沿用同一发送者
                    // 私聊：不需要 sender
                }
            }
        }
        
        return messages
    }
    
    /**
     * 打印节点树结构用于分析 TIM/QQ 的消息布局
     * 打印所有有文字的节点
     */
    private fun dumpNodeStructure(node: AccessibilityNodeInfo, depth: Int, path: String = "") {
        val className = node.className?.toString()?.substringAfterLast(".") ?: "?"
        val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""
        val text = node.text?.toString()?.take(50)?.replace("\n", "\\n") ?: ""
        val contentDesc = node.contentDescription?.toString()?.take(30) ?: ""
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        
        val currentPath = if (viewId.isNotEmpty()) "$path/$viewId" else "$path/$className"
        
        // 打印有文字或有 viewId 的节点
        if (text.isNotEmpty() || contentDesc.isNotEmpty() || viewId.isNotEmpty()) {
        //if(true) {
            val indent = "  ".repeat(minOf(depth, 6))
            Log.d(TAG, "$indent[$className] id=$viewId x=${bounds.left} y=${bounds.top} text=\"$text\"")
        }
        
        if (depth < 50) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    dumpNodeStructure(child, depth + 1, currentPath)
                    child.recycle()
                }
            }
        }
    }

    // Track context for determining message sender
    private data class NodeContext(
        val isFromSelf: Boolean = false,
        val senderName: String? = null
    )

    private fun collectTextsFromNode(
        node: AccessibilityNodeInfo,
        textParts: MutableList<String>,
        depth: Int,
        debugLog: StringBuilder
    ) {
        try {
            // Get node properties
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()

            // Add text to parts (pure text, no sender info)
            if (!text.isNullOrBlank()) {
                textParts.add(text)
            }

            // Also check contentDescription
            if (!contentDesc.isNullOrBlank() && contentDesc != text) {
                textParts.add(contentDesc)
            }

            // Recursively process children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectTextsFromNode(child, textParts, depth + 1, debugLog)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error at depth $depth: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseAsMessage(text: String, isFromSelf: Boolean = false): ChatMessage? {
        // Skip very short texts (likely UI elements)
        if (text.length < 2) return null

        // Skip common UI elements
        val skipPatterns = listOf(
            "发送", "返回", "更多", "语音", "表情", "相册", "拍摄",
            "视频通话", "语音通话", "转账", "红包", "位置",
            "Send", "Back", "More", "Voice", "Photo",
            "按住说话", "输入", "消息", "通话"
        )
        if (skipPatterns.any { text.contains(it, ignoreCase = true) && text.length < 15 }) {
            return null
        }

        // Skip time-only texts
        val timePattern = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")
        if (timePattern.matches(text.trim())) return null
        
        // Skip date patterns like "12月14日" or "今天" etc
        if (text.matches(Regex("^\\d{1,2}月\\d{1,2}日.*")) || 
            text in listOf("今天", "昨天", "前天")) {
            return null
        }

        return ChatMessage(
            sender = if (isFromSelf) "我" else "对方",
            content = text,
            isFromSelf = isFromSelf
        )
    }

    /**
     * Check if accessibility service is running
     */
    fun isAccessibilityEnabled(): Boolean {
        return ChatAccessibilityService.instance != null
    }
    
    /**
     * 合并两次采集的消息，基于重叠区域去重
     * 向上滚动时：newBatch 是更早的消息，应该放在 accumulated 前面
     * 保留真正的重复消息，只去除滚动产生的重复
     */
    fun mergeMessages(accumulated: List<ChatMessage>, newBatch: List<ChatMessage>): List<ChatMessage> {
        if (accumulated.isEmpty()) return newBatch
        if (newBatch.isEmpty()) return accumulated
        
        // 向上滚动时：newBatch 是更早的消息（屏幕上方）
        // newBatch 的尾部可能与 accumulated 的开头重叠
        val maxOverlap = minOf(accumulated.size, newBatch.size)
        
        for (overlapSize in maxOverlap downTo 1) {
            val newTail = newBatch.takeLast(overlapSize)
            val accHead = accumulated.take(overlapSize)
            
            // 比较序列是否匹配（sender + content 都相同）
            val matches = newTail.zip(accHead).all { (a, b) ->
                a.sender == b.sender && a.content == b.content && a.isFromSelf == b.isFromSelf
            }
            
            if (matches) {
                // 找到重叠：newBatch（更早）在前 + accumulated（去除重叠部分）
                return newBatch + accumulated.drop(overlapSize)
            }
        }
        
        // 没有找到重叠，newBatch（更早的）放前面
        return newBatch + accumulated
    }
    
    /**
     * 根据消息列表生成上下文字符串
     */
    fun buildContext(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") { msg ->
            val prefix = if (msg.isFromSelf) "[我]" else "[${msg.sender ?: "对方"}]"
            "$prefix: ${msg.content}"
        }.take(2000)
    }
}


