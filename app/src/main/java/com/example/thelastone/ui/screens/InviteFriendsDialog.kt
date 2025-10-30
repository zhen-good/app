package com.example.thelastone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.ui.screens.comp.Avatar
import com.example.thelastone.vm.InviteFriendsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendsDialog(
    tripId: String,
    onDismiss: () -> Unit,
    vm: InviteFriendsViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()

    // 成功後自動關閉
    LaunchedEffect(ui.done) { if (ui.done) onDismiss() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Invite friends") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.confirm() },
                        enabled = ui.selected.isNotEmpty() && !ui.submitting
                    ) { Text("Done") }
                }
            )
        }
    ) { padding ->
        when {
            ui.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            ui.error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("載入失敗：${ui.error}", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { vm.load() }) { Text("重試") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 搜尋列
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("搜尋朋友（姓名或 Email）") },
                    singleLine = true
                )

                // 快速動作：全選 / 清除
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AssistChip(
                        onClick = { vm.selectAllFiltered() },
                        label = { Text("全選顯示中") }
                    )
                    AssistChip(
                        onClick = { vm.clearSelection() },
                        label = { Text("清除選取") }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 名單
                val list = vm.filteredFriends()
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("沒有符合的朋友")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(list, key = { it.id }) { user ->
                            val isMember = user.id in ui.memberIds
                            val checked = user.id in ui.selected

                            ListItem(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable(enabled = !isMember) { vm.toggle(user.id) }
                                    .padding(horizontal = 4.dp),
                                leadingContent = {
                                    // 你已有的 Avatar() 元件；沒有的話可換成預設圖示
                                    Avatar(imageUrl = user.avatarUrl, size = 40.dp)
                                },
                                headlineContent = { Text(user.name) },
                                supportingContent = { Text(user.email) },
                                trailingContent = {
                                    if (isMember) {
                                        AssistChip(onClick = {}, enabled = false, label = { Text("已加入") })
                                    } else {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { vm.toggle(user.id) }
                                        )
                                    }
                                }
                            )
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // 送出中：右下角小進度以提示
        if (ui.submitting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
