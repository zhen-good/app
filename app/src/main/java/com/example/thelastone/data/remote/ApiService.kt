package com.example.thelastone.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 用於連接您自己 FastAPI 後端的 Retrofit 介面
 */
interface ApiService {

    /**
     * 呼叫後端以生成推薦行程
     */
    @POST("recommend")
    suspend fun getRecommendations(
        @Body request: RecommendRequest
    ): RecommendationResponse
}
