package com.neon10.ratatoskr.data.repo

import com.neon10.ratatoskr.data.db.AppDatabase
import com.neon10.ratatoskr.data.model.ReplyOptionEntity
import kotlinx.coroutines.flow.Flow

class ReplyOptionRepository(private val db: AppDatabase) {
    private val dao = db.replyOptionDao()

    fun recent(limit: Int = 20): Flow<List<ReplyOptionEntity>> = dao.recent(limit)

    suspend fun save(label: String, text: String, contextSnippet: String?) {
        dao.insert(ReplyOptionEntity(label = label, text = text, contextSnippet = contextSnippet))
    }

    suspend fun clearOld(keepMs: Long) {
        val threshold = System.currentTimeMillis() - keepMs
        dao.deleteOlderThan(threshold)
    }

    suspend fun clearAll() = dao.clearAll()
}
