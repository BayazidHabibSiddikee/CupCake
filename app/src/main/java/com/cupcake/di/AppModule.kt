package com.cupcake.di

import android.content.Context
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.LlamaEngine
import com.cupcake.ai.OllamaEngine
import com.cupcake.data.source.local.AppDatabase
import com.cupcake.data.source.remote.ApiClient
import com.cupcake.data.source.remote.ApiServices
import com.cupcake.domain.repository.ChatRepository
import com.cupcake.domain.repository.ModelRepository
import com.cupcake.domain.repository.SystemPromptRepository
import com.cupcake.game.GameManager
import com.cupcake.game.GameLLMRouter
import com.cupcake.game.PointsManager
import com.cupcake.ui.screen.points.PointsViewModel
import com.cupcake.network.EspWebSocketServer
import com.cupcake.tts.TtsManager
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideOllamaEngine(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): OllamaEngine {
        // Initialize the singleton with the Hilt-provided OkHttpClient
        OllamaEngine.initialize(context, okHttpClient)
        return OllamaEngine.getInstance()
    }

    @Provides
    @Singleton
    fun provideCharacterManager(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): CharacterManager {
        CharacterManager.initialize(context.filesDir)
        return CharacterManager
    }

    @Provides
    @Singleton
    fun provideEnergyManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        adManager: com.cupcake.ai.AdManager,
        billingManager: com.cupcake.ai.BillingManager
    ): EnergyManager {
        return EnergyManager(context, adManager, billingManager)
    }

    @Provides
    @Singleton
    fun provideAdManager(): com.cupcake.ai.AdManager {
        // Swap for a real AdMob-backed implementation when ad unit IDs are configured.
        return com.cupcake.ai.FakeAdManager()
    }

    @Provides
    @Singleton
    fun provideBillingManager(): com.cupcake.ai.BillingManager {
        // Swap for a BillingClient-backed implementation when Play products are configured.
        return com.cupcake.ai.FakeBillingManager()
    }

    @Provides
    @Singleton
    fun provideLlamaEngine(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): LlamaEngine {
        LlamaEngine.initialize(context)
        return LlamaEngine.getInstance()
    }

    @Provides
    @Singleton
    fun provideTtsManager(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): TtsManager {
        val tts = TtsManager(context)
        tts.initialize()
        return tts
    }

    @Provides
    @Singleton
    fun provideLocalSpeaker(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        tts: TtsManager
    ): com.cupcake.tts.LocalSpeaker = com.cupcake.tts.LocalSpeaker(context, tts)

    @Provides
    @Singleton
    fun provideEspWebSocketServer(): EspWebSocketServer {
        return EspWebSocketServer
    }

    @Provides
    @Singleton
    fun provideGameManager(
        llmRouter: GameLLMRouter,
        characterManager: CharacterManager,
        pointsManager: PointsManager,
        sessionStore: com.cupcake.chat.ChatSessionStore
    ): GameManager {
        return GameManager(llmRouter, characterManager, pointsManager, sessionStore)
    }

    @Provides
    @Singleton
    fun providePointsStore(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): com.cupcake.game.PointsStore {
        return com.cupcake.game.SharedPrefsPointsStore(context)
    }

    @Provides
    @Singleton
    fun providePointsManager(store: com.cupcake.game.PointsStore): PointsManager {
        return PointsManager(store)
    }

    @Provides
    @Singleton
    fun providePointsViewModel(
        pointsManager: PointsManager,
        gameManager: GameManager
    ): PointsViewModel {
        return PointsViewModel(pointsManager, gameManager)
    }

    @Provides
    @Singleton
    fun provideExecutor(): ListeningExecutorService {
        return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .baseUrl("https://api.openai.com/v1/") // Default, overridden per request
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAiApi(retrofit: Retrofit): ApiServices.OpenAiApi {
        return retrofit.create(ApiServices.OpenAiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOllamaApi(retrofit: Retrofit): ApiServices.OllamaApi {
        return retrofit.create(ApiServices.OllamaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideApiClient(
        okHttpClient: OkHttpClient
    ): ApiClient {
        return ApiClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        database: AppDatabase,
        apiClient: ApiClient
    ): ChatRepository {
        return com.cupcake.data.repository.ChatRepositoryImpl(database, apiClient)
    }

    @Provides
    @Singleton
    fun provideSystemPromptRepository(
        database: AppDatabase
    ): SystemPromptRepository {
        return com.cupcake.data.repository.SystemPromptRepositoryImpl(database)
    }

    @Provides
    @Singleton
    fun provideModelRepository(
        database: AppDatabase,
        apiClient: ApiClient
    ): ModelRepository {
        return com.cupcake.data.repository.ModelRepositoryImpl(database, apiClient)
    }
}