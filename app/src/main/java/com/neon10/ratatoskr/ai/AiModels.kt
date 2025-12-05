package com.neon10.ratatoskr.ai

data class ReplyOption(
    val title: String,
    val text: String
)

data class StylePrefs(
    val safe: Float = 0.5f,
    val proactive: Float = 0.3f,
    val humor: Float = 0.2f
)
