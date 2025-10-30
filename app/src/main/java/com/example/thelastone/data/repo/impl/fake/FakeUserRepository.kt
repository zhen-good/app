//package com.example.thelastone.data.repo.impl.fake
//
//import com.example.thelastone.data.model.AuthUser
//import com.example.thelastone.data.model.FriendRequest
//import com.example.thelastone.data.model.User
//import com.example.thelastone.data.repo.UserRepository
//import com.example.thelastone.di.SessionManager
//import kotlinx.coroutines.delay
//import java.util.UUID
//import javax.inject.Inject
//import javax.inject.Singleton
//
//@Singleton
//class FakeUserRepository @Inject constructor(
//    private val session: SessionManager
//) : UserRepository {
//
//    // ---- 假資料存放 ----
//    private val users = mutableMapOf<String, User>(
//        "demo-user" to User("demo-user", "Demo User", "demo@example.com", null, friends = listOf("friend-1")),
//        "friend-1"  to User("friend-1",  "Alice Chen", "alice@ex.com", null, friends = listOf("demo-user", "friend-2")),
//        "friend-2"  to User("friend-2",  "Bob Wang",   "bob@ex.com",   null, friends = listOf("friend-1")),
//        "friend-3"  to User("friend-3",  "Cathy Lin",  "cathy@ex.com", null),
//        "friend-4"  to User("friend-4",  "David Wu",   "david@ex.com", null),
//    )
//
//    // 別人寄給「我」的邀請
//    private val friendRequests = mutableListOf<FriendRequest>(
//        FriendRequest(id = "req-1", fromUserId = "friend-3", toUserId = "demo-user", status = "pending")
//    )
//
//    override suspend fun login(email: String, password: String): AuthUser {
//        delay(200)
//        val user = users.values.firstOrNull { it.email == email } ?: users.getValue("demo-user")
//        val auth = AuthUser(token = "fake-token-${user.id}", user = user)
//        session.setAuth(auth)
//        return auth
//    }
//
//    override suspend fun register(name: String, email: String, password: String): AuthUser {
//        delay(200)
//        val id = UUID.randomUUID().toString()
//        val new = User(id = id, name = name, email = email, avatarUrl = null, friends = emptyList())
//        users[id] = new
//        val auth = AuthUser(token = "fake-token-$id", user = new)
//        session.setAuth(auth)
//        return auth
//    }
//
//    override suspend fun logout() { delay(100); session.clear() }
//
//    override suspend fun getFriends(): List<User> {
//        delay(120)
//        val me = session.currentUserId
//        val ids = users[me]?.friends ?: emptyList()
//        return ids.mapNotNull { users[it] }
//    }
//
//    override suspend fun searchUsers(keyword: String): List<User> {
//        delay(150)
//        if (keyword.isBlank()) return emptyList()
//        val me = session.currentUserId
//        return users.values
//            .filter { it.id != me }
//            .filter { it.name.contains(keyword, ignoreCase = true) || it.email.contains(keyword, ignoreCase = true) }
//            .sortedBy { it.name }
//    }
//
//    override suspend fun sendFriendRequest(toUserId: String) {
//        delay(150)
//        val me = session.currentUserId
//        if (toUserId == me) return
//        // 已是朋友就不重送
//        if (users[me]?.friends?.contains(toUserId) == true) return
//        // 已有 pending 的就不重送
//        val exist = friendRequests.any { it.fromUserId == me && it.toUserId == toUserId && it.status == "pending" }
//        if (exist) return
//        friendRequests += FriendRequest(
//            id = "req-${UUID.randomUUID()}",
//            fromUserId = me,
//            toUserId = toUserId,
//            status = "pending"
//        )
//    }
//
//    override suspend fun getIncomingFriendRequests(): List<FriendRequest> {
//        delay(120)
//        val me = session.currentUserId
//        return friendRequests.filter { it.toUserId == me && it.isPending }
//    }
//
//    override suspend fun acceptFriendRequest(requestId: String) {
//        delay(120)
//        val req = friendRequests.find { it.id == requestId && it.isPending } ?: return
//        val from = req.fromUserId
//        val to   = req.toUserId
//        // 建立雙向好友
//        users[from]?.let { u -> users[from] = u.copy(friends = (u.friends + to).distinct()) }
//        users[to]?.let   { u -> users[to]   = u.copy(friends = (u.friends + from).distinct()) }
//        // 更新狀態
//        val idx = friendRequests.indexOf(req)
//        friendRequests[idx] = req.copy(status = "accepted")
//    }
//
//    override suspend fun rejectFriendRequest(requestId: String) {
//        delay(120)
//        val idx = friendRequests.indexOfFirst { it.id == requestId && it.isPending }
//        if (idx >= 0) friendRequests[idx] = friendRequests[idx].copy(status = "rejected")
//    }
//
//    override suspend fun getUserById(userId: String): User? = users[userId]
//
//    override suspend fun updateProfile(name: String?, avatarUrl: String?): User {
//        delay(150)
//        val me = session.currentUserId
//        val curr = users.getValue(me)
//        val updated = curr.copy(
//            name = name ?: curr.name,
//            avatarUrl = avatarUrl ?: curr.avatarUrl
//        )
//        users[me] = updated
//        // 同步 Session 中的 AuthUser
//        session.setAuth(AuthUser(token = session.auth.value!!.token, user = updated))
//        return updated
//    }
//}
//
