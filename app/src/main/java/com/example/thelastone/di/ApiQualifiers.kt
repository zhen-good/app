package com.example.thelastone.di

import javax.inject.Qualifier

/**
 * 一個限定符，用於標記與「推薦 API」相關的依賴
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecommendApi

/**
 * 另一個限定符，用於標記與「Google API」相關的依賴
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleApi
