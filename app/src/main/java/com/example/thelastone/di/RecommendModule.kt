package com.example.thelastone.di

import com.example.thelastone.data.remote.ApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecommendModule {

    // 您的後端 API 的 Base URL (for Android Emulator)
    private const val RECOMMEND_BASE_URL = "http://10.0.2.2:8000/"

    @Provides
    @Singleton
    @RecommendApi // 標記為 "推薦 API" 專用
    fun provideRecommendOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    @Provides
    @Singleton
    @RecommendApi // 標記
    fun provideRecommendRetrofit(
        @RecommendApi okHttpClient: OkHttpClient, // 指定使用 @RecommendApi 的 client
        json: Json // 來自您的 SerializationModule
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(RECOMMEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(@RecommendApi retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
