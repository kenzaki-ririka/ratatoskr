package com.neon10.ratatoskr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.neon10.ratatoskr.data.dao.ReplyOptionDao
import com.neon10.ratatoskr.data.model.ReplyOptionEntity

@Database(
    entities = [ReplyOptionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun replyOptionDao(): ReplyOptionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ratatoskr.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
