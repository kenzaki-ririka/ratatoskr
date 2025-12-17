package com.neon10.ratatoskr.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Native Android AccessibilityService for collecting chat messages.
 * Using native service instead of Assists library may work better with WeChat.
 */
class ChatAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "ChatA11yService"
        
        @Volatile
        var instance: ChatAccessibilityService? = null
            private set
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only need on-demand collection, not continuous monitoring
        // So we don't need to handle events here
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Service destroyed")
    }
    
    /**
     * Get the root node of the current active window.
     * This uses the native Android API directly.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root node: ${e.message}")
            null
        }
    }
    
    /**
     * Get all windows (requires API 21+)
     */
    fun getAllWindows() = windows
    
    /**
     * 向上滚动屏幕（用于采集更多聊天记录）
     * @param callback 滚动完成后的回调
     */
    fun scrollUp(callback: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Gesture API requires API 24+")
            callback?.invoke()
            return
        }
        
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 从屏幕中间偏上向下滑动（模拟向上滚动查看历史消息）
            val startX = screenWidth / 2f
            val startY = screenHeight * 0.3f  // 从屏幕30%高度开始
            val endY = screenHeight * 0.7f    // 滑到屏幕70%高度
            
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Scroll up completed")
                    callback?.invoke()
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Scroll up cancelled")
                    callback?.invoke()
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error scrolling: ${e.message}")
            callback?.invoke()
        }
    }
}
