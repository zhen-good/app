package com.example.thelastone.data.model

data class AuthUser(
    val token: String,              // 後端簽發 JWT 或類似憑證
    val user: User
)
