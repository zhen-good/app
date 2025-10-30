package com.example.thelastone.di

import com.example.thelastone.data.repo.StartRepository
import com.example.thelastone.data.repo.impl.StartRepositoryFake
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StartModule {
    @Provides @Singleton
    fun provideStartRepository(): StartRepository = StartRepositoryFake()
}
