package io.github.astromg01.nucleo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "projects",
    indices = [Index(value = ["treeUri"], unique = true)],
)
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val treeUri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["projectId"]), Index(value = ["status"]), Index(value = ["updatedAt"])],
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val shareProjectContext: Boolean = false,
    val title: String,
    val prompt: String,
    val status: String = TaskStatus.QUEUED.name,
    val progress: Int = 0,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val resultText: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "memories",
    indices = [Index(value = ["projectId"]), Index(value = ["kind"]), Index(value = ["updatedAt"]), Index(value = ["sourceTaskId"], unique = true)],
)
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val sourceTaskId: String? = null,
    val kind: String,
    val title: String,
    val content: String,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"]), Index(value = ["createdAt"])],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "main",
    val role: String,
    val content: String,
    val provider: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Lightweight projection for conversation switching; message bodies remain in the local Room database. */
data class ChatSessionSummary(
    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "messageCount") val messageCount: Long,
)

enum class TaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    BLOCKED,
    CANCELLED,
}
