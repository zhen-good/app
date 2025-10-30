// vm/AuthViewModel.kt
package com.example.thelastone.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.AuthUser
import com.example.thelastone.data.repo.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.thelastone.data.repo.UserRepository
import com.example.thelastone.di.SessionManager

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepo: UserRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val auth: StateFlow<AuthUser?> = sessionManager.auth  // ← 有這個嗎？
    val state: StateFlow<AuthUiState> = _state

    fun updateEmail(v: String)    { _state.value = _state.value.copy(email = v) }
    fun updatePassword(v: String) { _state.value = _state.value.copy(password = v) }
    fun updateName(v: String)     { _state.value = _state.value.copy(name = v) }
    fun clearError()              { _state.value = _state.value.copy(error = null) }

    fun login() = viewModelScope.launch {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "請輸入 Email/密碼")
            return@launch
        }

        _state.value = s.copy(loading = true, error = null)

        val result = runCatching {
            userRepo.login(s.email, s.password)
        }.getOrElse { e ->
            Log.e("AuthViewModel", "Login exception", e)
            _state.value = _state.value.copy(loading = false, error = e.message ?: "登入失敗")
            return@launch
        }

        Log.d("AuthViewModel", "Login result: $result")

        when (result) {
            is AuthResult.Success -> {
                Log.d("AuthViewModel", "Setting auth: ${result.authUser}")
                sessionManager.setAuth(result.authUser)
                Log.d("AuthViewModel", "Current sessionManager.auth: ${sessionManager.auth.value}")
                _state.value = _state.value.copy(loading = false)
            }
            is AuthResult.Error -> {
                Log.e("AuthViewModel", "Login error: ${result.message}")
                _state.value = _state.value.copy(loading = false, error = result.message)
            }
        }
    }

    fun register() = viewModelScope.launch {
        val s = _state.value
        if (s.name.isBlank() || s.email.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "請完整填寫"); return@launch
        }
        _state.value = s.copy(loading = true, error = null)
        runCatching { userRepo.register(s.name, s.email, s.password) }
            .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "註冊失敗") }
            .onSuccess  { _state.value = _state.value.copy(loading = false) }
    }
}