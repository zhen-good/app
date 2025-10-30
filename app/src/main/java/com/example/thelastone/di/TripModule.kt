package com.example.thelastone.di

import com.example.thelastone.data.local.SavedPlaceDao
import com.example.thelastone.data.repo.SavedRepository
import com.example.thelastone.data.repo.impl.fake.FakeTripRepository
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.data.repo.impl.SavedRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// di/TripModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class TripModule {
/*
@Binds @Singleton
abstract fun bindTripRepository(
   impl: FakeTripRepository
): TripRepository
 */
}
