package com.example.thelastone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.data.model.User
import com.example.thelastone.ui.screens.comp.Avatar
import com.example.thelastone.vm.SearchUsersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUsersScreen(
    padding: PaddingValues,
    vm: SearchUsersViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val ui by vm.state.collectAsState()

    // 與 SearchPlacesScreen 一致：預設展開、聚焦鍵盤
    var active by rememberSaveable { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var requestedFocus by rememberSaveable { mutableStateOf(false) }

    // 一次返回：若有 Dialog 先關，否則直接 navigateUp()
    BackHandler {
        if (ui.dialogUser != null) {
            vm.closeDialog()
        } else {
            onBack()
        }
    }

    LaunchedEffect(active) {
        if (active && !requestedFocus) {
            requestedFocus = true
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            query = ui.query,
            onQueryChange = vm::onQueryChange,
            onSearch = { keyboard?.hide() }, // 由 VM 的 debounce 觸發搜尋
            active = active,
            onActiveChange = { active = it },
            placeholder = { Text("搜尋名稱或 Email") },
            leadingIcon = {
                // 一次返回
                IconButton(onClick = { onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            trailingIcon = {
                when {
                    ui.loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    ui.query.isNotEmpty() -> {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                }
            }
        ) {
            when {
                ui.error != null -> {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("搜尋失敗：${ui.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
                !ui.loading && ui.results.isEmpty() && ui.query.isNotBlank() -> {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("找不到相關使用者")
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().imePadding()) {
                        items(ui.results, key = { it.id }) { u ->
                            UserRow(user = u, onClick = { vm.openDialog(u) })
                            Divider()
                        }
                    }
                }
            }
        }
    }

    // Dialog：沿用你的版本
    ui.dialogUser?.let { dlg ->
        UserPreviewDialog(
            user = dlg,
            sending = ui.sending,
            sentSuccess = ui.sentSuccess,
            onSend = { vm.sendRequest() },
            onDismiss = { vm.closeDialog() }
        )
    }
}

@Composable
private fun UserRow(user: User, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Avatar(imageUrl = user.avatarUrl, size = 40.dp) },
        headlineContent = { Text(user.name) },
        supportingContent = { Text(user.email) },
        trailingContent = { /* 更多操作 */ },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun UserPreviewDialog(
    user: User,
    sending: Boolean,
    sentSuccess: Boolean,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (sentSuccess) {
                TextButton(onClick = onDismiss) { Text("關閉") }
            } else {
                TextButton(onClick = onSend, enabled = !sending) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("送出好友邀請")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        icon = { Avatar(imageUrl = user.avatarUrl, size = 48.dp) },
        title = { Text(user.name) },
        text = { Text("好友數：${user.friends.size}") }
    )
}