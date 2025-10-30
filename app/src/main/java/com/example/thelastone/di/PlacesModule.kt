package com.example.thelastone.di

import com.example.thelastone.BuildConfig
import com.example.thelastone.data.remote.PlacesApi
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
object PlacesModule {

    @Provides
    @Singleton
    @GoogleApi // 標記：這是給 Google API 用的 OkHttpClient
    fun provideGoogleOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Goog-Api-Key", BuildConfig.MAPS_API_KEY)
                    .build()
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    @GoogleApi // 標記：這是給 Google API 用的 Retrofit
    fun provideGoogleRetrofit(
        @GoogleApi okHttpClient: OkHttpClient, // 指定：使用帶 @GoogleApi 標籤的 client
        json: Json // 來自您的 SerializationModule
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @GoogleApi // 👈 [ [ [ 最重要的修改：在這裡也加上 @GoogleApi 標籤 ] ] ]
    fun providePlaceApi(
        @GoogleApi retrofit: Retrofit // 指定：使用帶 @GoogleApi 標籤的 Retrofit
    ): PlacesApi {
        return retrofit.create(PlacesApi::class.java)
    }
}

