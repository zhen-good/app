// data/repo/UserRepository.kt
package com.example.thelastone.data.repo

import com.example.thelastone.data.model.AuthUser
import com.example.thelastone.data.model.FriendRequest
import com.example.thelastone.data.model.User


// ===== Repository 介面 =====
interface UserRepository {
    // 認證相關
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(name:String,email: String, password: String): RegisterResult
    suspend fun logout()

    // 朋友/搜尋
    suspend fun getFriends(): List<User>
    suspend fun searchUsers(keyword: String): List<User>
    suspend fun sendFriendRequest(toUserId: String)

    // 好友邀請
    suspend fun getIncomingFriendRequests(): List<FriendRequest>
    suspend fun acceptFriendRequest(requestId: String)
    suspend fun rejectFriendRequest(requestId: String)

    // 其他
    suspend fun getUserById(userId: String): User?
    suspend fun updateProfile(name: String? = null, avatarUrl: String? = null): User
}