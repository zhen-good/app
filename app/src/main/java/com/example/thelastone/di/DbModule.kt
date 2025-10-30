package com.example.thelastone.di

import android.content.Context
import androidx.room.Room
import com.example.thelastone.data.local.AppDatabase
import com.example.thelastone.data.local.MessageDao
import com.example.thelastone.data.local.SavedPlaceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "app.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides fun provideSavedPlaceDao(db: AppDatabase): SavedPlaceDao = db.savedPlaceDao()
}
