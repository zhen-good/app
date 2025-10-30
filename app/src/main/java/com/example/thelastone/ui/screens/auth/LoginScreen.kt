// ui/screens/auth/LoginScreen.kt
package com.example.thelastone.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.vm.AuthViewModel
import androidx.compose.runtime.LaunchedEffect


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsState()
    val auth by vm.sessionManager.auth.collectAsState()  // ✅ 加這行

    // ✅ 監聽登入成功狀態
    LaunchedEffect(auth) {
        if (auth != null) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("登入") }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = s.email,
                onValueChange = { vm.updateEmail(it); vm.clearError() },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()  // ✅ 建議加上
            )
            OutlinedTextField(
                value = s.password,
                onValueChange = { vm.updatePassword(it); vm.clearError() },
                label = { Text("密碼") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()  // ✅ 建議加上
            )

            if (s.error != null) Text(s.error!!, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = vm::login,
                enabled = !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (s.loading) "登入中…" else "登入") }

            TextButton(
                onClick = onRegister,
                enabled = !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("沒有帳號？前往註冊") }
        }
    }
}