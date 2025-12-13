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
            Log.d(TAG, "Service instance: ${service != null}")
            
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
            Log.d(TAG, "Active Window - Package: $appName, Root node: ${rootNode != null}")
            
            // Check if root node is empty (WeChat protection)
            val isEmptyRoot = rootNode != null && rootNode.childCount == 0 && rootNode.className == null
            if (isEmptyRoot) {
                debugLog.appendLine("  Root is empty (possible protection), trying windows...")
                Log.d(TAG, "Root is empty, trying getWindows()")
                rootNode?.recycle()
                rootNode = null
            }
            
            // Try method 2: Get all windows and find the right one
            if (rootNode == null || isEmptyRoot) {
                debugLog.appendLine("Method 2 - Windows API:")
                val windows = service.windows
                debugLog.appendLine("  Total windows: ${windows?.size ?: 0}")
                Log.d(TAG, "Total windows: ${windows?.size ?: 0}")
                
                windows?.forEachIndexed { index, window ->
                    val windowRoot = window.root
                    val pkg = windowRoot?.packageName?.toString()
                    val childCount = windowRoot?.childCount ?: 0
                    val className = windowRoot?.className?.toString()
                    debugLog.appendLine("  Window[$index]: pkg=$pkg, class=$className, children=$childCount")
                    Log.d(TAG, "Window[$index]: pkg=$pkg, class=$className, children=$childCount")
                    
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
                Log.d(TAG, "Final Root class: ${rootNode.className}, childCount: ${rootNode.childCount}")
                debugLog.appendLine("Final root - class: ${rootNode.className}, children: ${rootNode.childCount}")
                
                // Recursively traverse all nodes to collect text content
                collectTextsFromNode(rootNode, textParts, messages, 0, debugLog)
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
        Log.d(TAG, "Total texts: ${textParts.size}, messages: ${messages.size}")

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

    private fun collectTextsFromNode(
        node: AccessibilityNodeInfo,
        textParts: MutableList<String>,
        messages: MutableList<ChatMessage>,
        depth: Int,
        debugLog: StringBuilder
    ) {
        try {
            val indent = "  ".repeat(depth)
            
            // Get text from this node
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            val className = node.className?.toString() ?: "unknown"
            
            // Log first 3 levels for debugging
            if (depth < 3) {
                val info = "$indent[$className] text='${text?.take(30) ?: "null"}' desc='${contentDesc?.take(30) ?: "null"}' children=${node.childCount}"
                Log.d(TAG, info)
                if (depth < 2) {
                    debugLog.appendLine(info)
                }
            }

            // Add non-empty text
            if (!text.isNullOrBlank()) {
                textParts.add(text)
                // Try to parse as a chat message
                parseAsMessage(text)?.let { messages.add(it) }
            }

            // Also check contentDescription
            if (!contentDesc.isNullOrBlank() && contentDesc != text) {
                textParts.add(contentDesc)
            }

            // Recursively process children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectTextsFromNode(child, textParts, messages, depth + 1, debugLog)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error at depth $depth: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseAsMessage(text: String): ChatMessage? {
        // Skip very short texts (likely UI elements)
        if (text.length < 2) return null

        // Skip common UI elements
        val skipPatterns = listOf(
            "发送", "返回", "更多", "语音", "表情", "相册", "拍摄",
            "视频通话", "语音通话", "转账", "红包", "位置",
            "Send", "Back", "More", "Voice", "Photo"
        )
        if (skipPatterns.any { text.contains(it, ignoreCase = true) && text.length < 10 }) {
            return null
        }

        // Skip time-only texts
        val timePattern = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")
        if (timePattern.matches(text.trim())) return null

        // For now, we can't reliably determine sender without more context
        // Just return as a generic message
        return ChatMessage(
            sender = null,
            content = text,
            isFromSelf = false
        )
    }

    /**
     * Check if accessibility service is running
     */
    fun isAccessibilityEnabled(): Boolean {
        return ChatAccessibilityService.instance != null
    }
}


