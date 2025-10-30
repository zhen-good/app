// data/repo/impl/UserRepositoryImpl.kt
package com.example.thelastone.data.repo.impl

import android.util.Log
import com.example.thelastone.data.remote.AuthApiService
import com.example.thelastone.data.model.*
import com.example.thelastone.data.repo.UserRepository
import com.example.thelastone.data.repo.AuthResult  // ← 改成 import 我們自己的
import com.example.thelastone.data.repo.RegisterResult  // ← 加入這個
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val authApi: AuthApiService
) : UserRepository {

    // ===== 登入 =====
    override suspend fun login(email: String, password: String): AuthResult {
        return try {
            val response = authApi.login(LoginRequest(email, password))

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                val user = User(
                    id = body.user._id ?: body.user.email,
                    name = body.user.username,
                    email = body.user.email,
                    avatarUrl = null,
                    friends = emptyList()
                )

                val authUser = AuthUser(
                    token = body.token,
                    user = user
                )
                // ✅ Debug：印出 token 和 user
                Log.d("AuthRepo", "Token: ${body.token}")
                Log.d("AuthRepo", "User: ${body.user}")

                AuthResult.Success(authUser)
            } else {
                val errorBody = response.errorBody()?.string()
                AuthResult.Error(errorBody ?: "登入失敗")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "連線失敗")
        }
    }

    // ===== 註冊 =====
    override suspend fun register(name:String,email: String, password: String): RegisterResult {
        return try {
            val response = authApi.register(RegisterRequest(email, password))

            if (response.isSuccessful && response.body() != null) {
                val body: RegisterResponse = response.body()!!  // ← 明確指定類型
                val message: String = body.message  // ← 明確指定類型
                RegisterResult.Success(message)
            } else {
                RegisterResult.Error(response.errorBody()?.string() ?: "註冊失敗")
            }
        } catch (e: Exception) {
            RegisterResult.Error(e.message ?: "連線失敗")
        }
    }

    // ===== 登出 =====
    override suspend fun logout() {
        // 之後實作清除 token 的邏輯
    }

    // ===== 其他方法先保留空實作 =====
    override suspend fun getFriends(): List<User> {
        TODO("之後實作")
    }

    override suspend fun searchUsers(keyword: String): List<User> {
        TODO("之後實作")
    }

    override suspend fun sendFriendRequest(toUserId: String) {
        TODO("之後實作")
    }

    override suspend fun getIncomingFriendRequests(): List<FriendRequest> {
        TODO("之後實作")
    }

    override suspend fun acceptFriendRequest(requestId: String) {
        TODO("之後實作")
    }

    override suspend fun rejectFriendRequest(requestId: String) {
        TODO("之後實作")
    }

    override suspend fun getUserById(userId: String): User? {
        TODO("之後實作")
    }

    override suspend fun updateProfile(name: String?, avatarUrl: String?): User {
        TODO("之後實作")
    }

}
