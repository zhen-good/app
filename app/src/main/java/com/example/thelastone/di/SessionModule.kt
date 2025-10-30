package com.example.thelastone.di

import com.example.thelastone.data.model.AuthUser
import com.example.thelastone.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext


//// di/SessionModule.kt
//@Module
//@InstallIn(SingletonComponent::class)
//object SessionModule {
//    @Provides @Singleton
//    fun provideSessionManager(): SessionManager = SessionManager().apply {  }
//}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _auth = MutableStateFlow<AuthUser?>(null)
    val auth: StateFlow<AuthUser?> = _auth

    val isLoggedIn: Boolean get() = _auth.value != null
    val currentUserId: String get() = _auth.value?.user?.id
        ?: error("No user. Require login.")

    init {
        // ✅ App 啟動時從 SharedPreferences 讀取
        val json = prefs.getString("auth_user", null)
        if (json != null) {
            try {
                _auth.value = gson.fromJson(json, AuthUser::class.java)
            } catch (e: Exception) {
                // 解析失敗就清除
                prefs.edit().remove("auth_user").apply()
            }
        }
    }

    fun setAuth(auth: AuthUser?) {
        _auth.value = auth
        // ✅ 儲存到 SharedPreferences
        prefs.edit().apply {
            if (auth != null) {
                putString("auth_user", gson.toJson(auth))
            } else {
                remove("auth_user")
            }
            apply()
        }
    }

    fun setDemoUser() { setAuth(DEMO_AUTH) }
    fun clear() { setAuth(null) }
}

// 一處集中定義 Demo 帳號
val DEMO_USER = User(
    id = "demo-user",
    name = "Demo User",
    email = "demo@example.com",
    avatarUrl = null,
    friends = listOf("friend-1", "friend-2")
)
val DEMO_AUTH = AuthUser(
    token = "demo-token",
    user = DEMO_USER
)