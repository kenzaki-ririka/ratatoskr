package com.neon10.ratatoskr.ai

interface AiService {
    suspend fun generateReplies(context: String?, limit: Int = 3, prefs: StylePrefs = StylePrefs()): List<ReplyOption>
}
