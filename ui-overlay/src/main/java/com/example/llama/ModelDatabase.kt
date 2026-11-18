package com.example.llama

import android.content.Context
import androidx.room.*

@Entity(tableName = "my_models")
data class LocalModel(
    @PrimaryKey val name: String,
    val path: String,
    val size: Long
)

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalModel)

    @Query("SELECT * FROM my_models")
    suspend fun getAllModels(): List<LocalModel>
}

@Database(entities = [LocalModel::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
