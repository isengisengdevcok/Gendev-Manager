package com.example.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val category: String,
    val openedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "editor_drafts")
data class EditorDraftEntity(
    @PrimaryKey val filePath: String,
    val content: String,
    val languageName: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "terminal_history")
data class TerminalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val executedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val providerTag: String,
    val attachedContextLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteByPath(path: String)
}

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY openedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE category = 'APK_BUNDLE' ORDER BY openedAt DESC LIMIT 10")
    fun observeRecentApks(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentFileEntity)
}

@Dao
interface EditorDraftDao {
    @Query("SELECT * FROM editor_drafts WHERE filePath = :path LIMIT 1")
    suspend fun getDraft(path: String): EditorDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: EditorDraftEntity)

    @Query("DELETE FROM editor_drafts WHERE filePath = :path")
    suspend fun clearDraft(path: String)
}

@Dao
interface TerminalHistoryDao {
    @Query("SELECT * FROM terminal_history ORDER BY executedAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<TerminalHistoryEntity>>

    @Insert
    suspend fun insert(item: TerminalHistoryEntity)
}

@Dao
interface AiMessageDao {
    @Query("SELECT * FROM ai_messages ORDER BY timestamp ASC")
    fun observeMessages(): Flow<List<AiMessageEntity>>

    @Insert
    suspend fun insert(message: AiMessageEntity)

    @Query("DELETE FROM ai_messages")
    suspend fun clearAll()
}

@Database(
    entities = [
        BookmarkEntity::class,
        RecentFileEntity::class,
        EditorDraftEntity::class,
        TerminalHistoryEntity::class,
        AiMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GenDevDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun editorDraftDao(): EditorDraftDao
    abstract fun terminalHistoryDao(): TerminalHistoryDao
    abstract fun aiMessageDao(): AiMessageDao

    companion object {
        @Volatile
        private var instance: GenDevDatabase? = null

        fun getInstance(context: Context): GenDevDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GenDevDatabase::class.java,
                    "gendev_manager.db"
                ).build().also { instance = it }
            }
        }
    }
}
