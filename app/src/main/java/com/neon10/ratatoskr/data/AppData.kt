package com.neon10.ratatoskr.data

import android.app.Application
import com.neon10.ratatoskr.data.db.AppDatabase
import com.neon10.ratatoskr.data.repo.ReplyOptionRepository

object AppData {
    lateinit var db: AppDatabase
    lateinit var replies: ReplyOptionRepository

    fun init(app: Application) {
        db = AppDatabase.get(app)
        replies = ReplyOptionRepository(db)
    }
}
