// ui/screens/auth/RegisterScreen.kt
package com.example.thelastone.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.vm.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBackToLogin: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsState()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("註冊") }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = s.name,
                onValueChange = { vm.updateName(it); vm.clearError() },
                label = { Text("名稱") },
                singleLine = true
            )
            OutlinedTextField(
                value = s.email,
                onValueChange = { vm.updateEmail(it); vm.clearError() },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = s.password,
                onValueChange = { vm.updatePassword(it); vm.clearError() },
                label = { Text("密碼") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            if (s.error != null) Text(s.error!!, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = vm::register,
                enabled = !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (s.loading) "建立中…" else "建立帳號") }

            TextButton(
                onClick = onBackToLogin,
                enabled = !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("已有帳號？返回登入") }
        }
    }
}
