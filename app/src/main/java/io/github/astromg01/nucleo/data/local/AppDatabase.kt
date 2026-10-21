package io.github.astromg01.nucleo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, TaskEntity::class, MemoryEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun chatMessageDao(): ChatMessageDao
}
