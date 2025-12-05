package com.neon10.ratatoskr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neon10.ratatoskr.data.model.ReplyOptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyOptionDao {
    @Insert
    suspend fun insert(option: ReplyOptionEntity): Long

    @Query("SELECT * FROM reply_options ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<ReplyOptionEntity>>

    @Query("DELETE FROM reply_options WHERE createdAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM reply_options")
    suspend fun clearAll()
}
