package com.example.thelastone.di

import android.content.Context
import android.location.Geocoder
import com.example.thelastone.data.remote.AuthApiService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.example.thelastone.data.remote.ChatService  // 🆕 添加這個 import



@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // 改成你的後端 API 網址
    private const val BASE_URL = "http://10.0.2.2:5000" // Android 模擬器用
    // 實體裝置改成: "http://你的電腦IP:3000/"


    /**
     * 提供 FusedLocationProviderClient 的單例實例。
     * 這是 Google Play Services 中用於取得裝置位置的主要 API。
     */
    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * 提供 Geocoder 的單例實例。
     * Geocoder 用於將經緯度座標轉換為地址，反之亦然。
     * 我們將其設定為使用台灣的地區設定。
     */
    @Provides
    @Singleton
    fun provideGeocoder(@ApplicationContext context: Context): Geocoder {
        return Geocoder(context, Locale.TAIWAN)
    }

    /**
     * 提供 OkHttpClient，用於網路請求的日誌記錄和設定。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 提供 Retrofit 實例，用於 API 呼叫。
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 提供 AuthApiService 實例。
     */
    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    // 聊天室
    @Provides
    @Singleton
    fun provideChatService(retrofit: Retrofit): ChatService {
        return retrofit.create(ChatService::class.java)
    }
}