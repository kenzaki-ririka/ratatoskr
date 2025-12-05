package com.neon10.ratatoskr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reply_options")
data class ReplyOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val text: String,
    val contextSnippet: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
