package com.example.thelastone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.data.model.User
import com.example.thelastone.ui.screens.comp.Avatar
import com.example.thelastone.vm.FriendStatsViewModel
import com.example.thelastone.vm.FriendsViewModel
import com.example.thelastone.vm.IncomingItem

@Composable
fun FriendsScreen(padding: PaddingValues, vm: FriendsViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsState()

    // ★ Dialog 狀態（兩種身分分開記）
    var previewIncoming by remember { mutableStateOf<IncomingItem?>(null) }
    var previewFriend by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (ui.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (ui.error != null) {
            Text("載入失敗：${ui.error}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.refresh() }) { Text("重試") }
            return
        }

        // ===== Section 1：等待回覆 =====
        Text("Waiting for reply", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.incoming.isEmpty()) {
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("目前沒有新的好友邀請")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(ui.incoming, key = { it.request.id }) { item ->
                    IncomingRow(
                        item = item,
                        onClick = { previewIncoming = item }, // ★ 點整列開 Dialog
                        onAccept = { vm.accept(item.request.id) },
                        onReject = { vm.reject(item.request.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== Section 2：我的好友 =====
        Text("My Friends", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.friends.isEmpty()) {
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("還沒有好友，點右上搜尋加好友！")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(ui.friends, key = { it.id }) { u ->
                    ListItem(
                        headlineContent = { Text(u.name) },
                        supportingContent = { Text(u.email) },
                        leadingContent = { Avatar(imageUrl = u.avatarUrl, size = 40.dp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { previewFriend = u } // ★ 點整列開 Dialog
                    )
                }
            }
        }
    }

    // ===== Dialogs =====
    previewIncoming?.let { item ->
        FriendInfoDialog(
            user = item.fromUser,
            onAccept = {
                vm.accept(item.request.id)
                previewIncoming = null
            },
            onReject = {
                vm.reject(item.request.id)
                previewIncoming = null
            },
            onDismiss = { previewIncoming = null }
        )
    }

// 2) 已是好友 — 只有關閉按鈕
    previewFriend?.let { user ->
        FriendInfoDialog(
            user = user,
            onDismiss = { previewFriend = null }
        )
    }
}

@Composable
private fun IncomingRow(
    item: IncomingItem,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    ListItem(
        leadingContent = { Avatar(imageUrl = item.fromUser.avatarUrl, size = 40.dp) },
        headlineContent = { Text(item.fromUser.name) },
        supportingContent = { Text("向你發出好友邀請") },
        trailingContent = {
            Row {
                IconButton(onClick = onReject) { Icon(Icons.Filled.Close, contentDescription = "拒絕") }
                IconButton(onClick = onAccept) { Icon(Icons.Filled.Check, contentDescription = "同意") }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // ★ 點整列開 Dialog
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendInfoDialog(
    user: User,
    onDismiss: () -> Unit,
    // ↓↓↓ 新增：可選的動作（有帶就顯示）
    onAccept: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    statsVm: FriendStatsViewModel = hiltViewModel()
) {
    val stats by statsVm.stats.collectAsState()
    val loading by statsVm.loading.collectAsState()
    val error by statsVm.error.collectAsState()

    LaunchedEffect(user.id) {
        statsVm.load(user.id)
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                // 標題區
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(imageUrl = user.avatarUrl, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(user.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 內容區
                when {
                    loading -> {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    error != null -> {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "載入統計失敗：$error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // ---------- 在 FriendInfoDialog() 內，替換 stats != null 區塊 ----------
                    stats != null -> {
                        Surface(
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatCell(
                                    value = stats!!.created,
                                    label = "Created",
                                    modifier = Modifier.weight(1f)
                                )

                                VerticalDivider(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .padding(horizontal = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                StatCell(
                                    value = stats!!.participating,
                                    label = "Participating",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 動作列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // 若為「等待回覆」情境，顯示 拒絕/同意
                    if (onReject != null && onAccept != null) {
                        TextButton(onClick = onReject) { Text("拒絕") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = onAccept) { Text("同意") }
                        Spacer(Modifier.width(8.dp))
                    }
                    // 通用的關閉
                    TextButton(onClick = onDismiss) { Text("關閉") }
                }
            }
        }
    }
}

// ---------- 請放在同檔案底部或適合的位置 ----------
@Composable
private fun StatCell(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall, // 粗一點、接近示意圖的大字
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
