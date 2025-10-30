// ui/navigation/AuthNav.kt
package com.example.thelastone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.thelastone.ui.screens.auth.LoginScreen
import com.example.thelastone.ui.screens.auth.RegisterScreen

object AuthRoutes {
    const val Login = "auth/login"
    const val Register = "auth/register"
}
@Composable
fun AuthNavHost(nav: NavHostController) {
    NavHost(
        navController = nav,
        startDestination = AuthRoutes.Register  // ← 改成先到註冊頁

    ) {
        composable(AuthRoutes.Login) {
            LoginScreen(
                onRegister = { nav.navigate(AuthRoutes.Register) },
                onLoginSuccess = { nav.navigate("home") }  // ✅ 加上這個！
            )
        }

        composable(AuthRoutes.Register) {
            RegisterScreen(
                onBackToLogin = {
                    nav.navigate(AuthRoutes.Login) {
                        popUpTo(AuthRoutes.Register) { inclusive = true }
                    }
                }
            )
        }
    }
}