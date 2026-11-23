package io.github.astromg01.nucleo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE treeUri = :treeUri LIMIT 1")
    suspend fun getByTreeUri(treeUri: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE kind IN ('chat_summary', 'chat_fact', 'chat_preference') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE kind = 'chat_summary' LIMIT 1")
    suspend fun chatSummary(): MemoryEntity?

    @Query("SELECT * FROM memories WHERE id = 'chat-summary-' || :sessionId AND kind = 'chat_summary' LIMIT 1")
    suspend fun chatSummaryForSession(sessionId: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE kind IN ('chat_fact', 'chat_preference') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun latestChatFacts(limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(memory: MemoryEntity)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChatMessageDao {
    @Query("""
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND id IN (
            SELECT id FROM chat_messages
            WHERE sessionId = :sessionId
            ORDER BY createdAt DESC, rowid DESC
            LIMIT :limit
          )
        ORDER BY createdAt ASC, rowid ASC
    """)
    fun observeRecentSession(sessionId: String, limit: Int): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT chat_messages.sessionId AS sessionId,
               COALESCE((
                   SELECT firstMessage.content FROM chat_messages AS firstMessage
                   WHERE firstMessage.sessionId = chat_messages.sessionId AND firstMessage.role = 'user'
                   ORDER BY firstMessage.createdAt ASC, firstMessage.rowid ASC
                   LIMIT 1
               ), 'Nova conversa') AS title,
               MAX(chat_messages.createdAt) AS updatedAt,
               COUNT(*) AS messageCount
        FROM chat_messages
        GROUP BY chat_messages.sessionId
        ORDER BY MAX(chat_messages.createdAt) DESC
    """)
    fun observeSessions(): Flow<List<ChatSessionSummary>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, rowid ASC")
    fun observeSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, rowid DESC LIMIT :limit")
    suspend fun latest(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND role IN ('user', 'assistant') ORDER BY createdAt DESC, rowid DESC LIMIT :limit")
    suspend fun latestConversation(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
}
