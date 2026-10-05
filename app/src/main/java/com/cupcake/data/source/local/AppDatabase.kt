package com.cupcake.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cupcake.data.model.ApiProvider
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.model.Conversation
import com.cupcake.data.model.ModelConfigWithConversation
import com.cupcake.data.model.SystemPrompt
import com.cupcake.data.source.local.converters.Converters
import com.cupcake.data.source.remote.ApiProviderDao

@Database(
    entities = [
        ChatMessage::class,
        Conversation::class,
        SystemPrompt::class,
        ApiProvider::class,
        ModelConfigWithConversation::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun systemPromptDao(): SystemPromptDao
    abstract fun apiProviderDao(): ApiProviderDao
    abstract fun modelConfigDao(): ModelConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cupcake.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}