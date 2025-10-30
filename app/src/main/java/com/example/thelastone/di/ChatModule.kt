// di/ChatModule.kt
package com.example.thelastone.di

import com.example.thelastone.data.local.MessageDao
import com.example.thelastone.data.remote.ChatService
import com.example.thelastone.data.remote.ChatWebSocketService
import com.example.thelastone.data.repo.ChatRepository
import com.example.thelastone.data.repo.impl.ChatRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository
}