package com.example.thelastone.data.repo

import com.example.thelastone.data.model.AuthUser

sealed class AuthResult {
    data class Success(val authUser: AuthUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class RegisterResult {
    data class Success(val message: String) : RegisterResult()
    data class Error(val message: String) : RegisterResult()
}