package com.example.thelastone.data.remote

import com.example.thelastone.data.model.LoginRequest
import com.example.thelastone.data.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import com.example.thelastone.data.model.RegisterRequest  // ← 加入這個
import com.example.thelastone.data.model.RegisterResponse  // ← 加入這個

interface AuthApiService {

    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>
}