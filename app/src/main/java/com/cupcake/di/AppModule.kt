package com.cupcake.di

import android.content.Context
import com.cupcake.data.source.local.AppDatabase
import com.cupcake.data.source.remote.ApiClient
import com.cupcake.data.source.remote.ApiServices
import com.cupcake.domain.repository.ChatRepository
import com.cupcake.domain.repository.ModelRepository
import com.cupcake.domain.repository.SystemPromptRepository
import com.cupcake.native.QwenNative
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
    fun provideQwenNative(): QwenNative {
        return QwenNative.getInstance()
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
        qwenNative: QwenNative,
        okHttpClient: OkHttpClient
    ): ApiClient {
        return ApiClient(qwenNative, okHttpClient)
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