package com.example.thelastone.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.ui.screens.comp.Avatar
import com.example.thelastone.vm.EditProfileEvent
import com.example.thelastone.vm.EditProfileViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    padding: PaddingValues,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val vm: EditProfileViewModel = hiltViewModel()
    val ui by vm.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 收一次性事件
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is EditProfileEvent.Saved -> {
                    // 讓外層決定導航與結果傳遞
                    onSaved()
                }
                is EditProfileEvent.Error -> {
                    snackbarHostState.showSnackbar(e.message)
                }
            }
        }
    }

    // Android Photo Picker 同原本...
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.onAvatarPicked(uri.toString())
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onCancel
                    ) { Text("Cancel") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { vm.save() },   // 只叫 VM，不直接導航
                        enabled = !ui.saving
                    ) {
                        if (ui.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save")
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 頭像 + 按鈕
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(imageUrl = ui.avatarUrl, size = 96.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Profile photo", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "JPG or PNG, square preferred",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) { Text("Change photo") }
                            }
                        }

                        // 名稱
                        OutlinedTextField(
                            value = ui.name,
                            onValueChange = vm::onNameChange,
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Email 一般不在這裡編（展示禁用）
                        OutlinedTextField(
                            value = ui.email,
                            onValueChange = {},
                            label = { Text("Email") },
                            singleLine = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Email is managed by authentication") }
                        )
                    }
                }
            }
        }
    }
}
