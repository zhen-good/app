package com.example.thelastone.di

import com.example.thelastone.data.remote.AuthApiService
import com.example.thelastone.data.repo.DefaultSpotRepository
import com.example.thelastone.data.repo.PlacesRepository
import com.example.thelastone.data.repo.SavedRepository
import com.example.thelastone.data.repo.SpotRepository
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.data.repo.UserRepository
import com.example.thelastone.data.repo.impl.PlacesRepositoryImpl
import com.example.thelastone.data.repo.impl.SavedRepositoryStub
import com.example.thelastone.data.repo.impl.TripRepositoryImpl
import com.example.thelastone.data.repo.impl.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds @Singleton
    abstract fun bindPlacesRepository(impl: PlacesRepositoryImpl): PlacesRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindSpotRepository(impl: DefaultSpotRepository): SpotRepository

    // 這條是你剛剛缺的：收藏 Stub 先綁上
    @Binds @Singleton
    abstract fun bindSavedRepository(impl: SavedRepositoryStub): SavedRepository
}