package com.example.thelastone.data.remote

import com.example.thelastone.data.model.Trip
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TripApiService {

    @GET("api/trips/{tripId}")
    suspend fun getTripDetail(
        @Path("tripId") tripId: String
    ): Trip

    // 或者如果是 query 參數：
    // @GET("api/trip/detail")
    // suspend fun getTripDetail(
    //     @Query("id") tripId: String
    // ): Trip
}