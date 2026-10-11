package com.cupcake.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.Conversation
import com.cupcake.data.model.ModelConfigWithConversation
import com.cupcake.data.model.SensorReading
import com.cupcake.data.model.SystemPrompt
import com.cupcake.data.source.local.converters.Converters
import com.cupcake.data.source.remote.ApiProviderDao

@Database(
    entities = [
        ChatMessage::class,
        Conversation::class,
        SystemPrompt::class,
        ApiProvider::class,
        ModelConfigWithConversation::class,
        SensorReading::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun systemPromptDao(): SystemPromptDao
    abstract fun apiProviderDao(): ApiProviderDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun sensorDao(): SensorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sensor_readings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        accelX REAL NOT NULL,
                        accelY REAL NOT NULL,
                        accelZ REAL NOT NULL,
                        gyroX REAL NOT NULL,
                        gyroY REAL NOT NULL,
                        gyroZ REAL NOT NULL,
                        temperature REAL NOT NULL,
                        batteryVoltage REAL NOT NULL,
                        sequence INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_readings_deviceId_timestamp ON sensor_readings (deviceId, timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_readings_sequence ON sensor_readings (sequence)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cupcake.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}