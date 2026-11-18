package com.example.llama

import android.content.Context
import androidx.room.*

@Entity(tableName = "my_models")
data class LocalModel(
    @PrimaryKey val name: String,
    val path: String,
    val size: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long
)

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalModel)

    @Query("SELECT * FROM my_models")
    suspend fun getAllModels(): List<LocalModel>

    @Delete
    suspend fun deleteModel(model: LocalModel)
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Database(entities = [LocalModel::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bonsai_models_db"
                )
                // Список моделей можно перекачать заново, деградация безопасна
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
