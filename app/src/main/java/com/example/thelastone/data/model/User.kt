package com.example.thelastone.data.model


// ===== 使用者資訊 =====
data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val friends: List<String> = emptyList()
)

// ===== API 請求 (Request) =====
data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String
)

// ===== API 回應 (Response) =====
data class LoginResponse(
    val message: String?,
    val redirect: String?,     // ✅ 加上這個（後端有回傳）
    val token: String,         // ✅ 加上這個（最重要！）
    val user: UserDto
)

data class RegisterResponse(
    val message: String,
    val detail: String? = null
)

// ===== MongoDB 使用者格式 (DTO) =====
// ← 加入這個!
data class UserDto(
    val _id: String?,           // MongoDB 的 _id
    val email: String,
    val username: String,
    val password: String? = null
)