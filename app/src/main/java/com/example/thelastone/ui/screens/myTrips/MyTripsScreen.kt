// ui/screens/MyTripsScreen.kt
package com.example.thelastone.ui.screens.myTrips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thelastone.ui.screens.comp.TripList
import com.example.thelastone.ui.state.EmptyState
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.vm.MyTripsUiState
import com.example.thelastone.vm.MyTripsViewModel

@Composable
fun MyTripsScreen(
    padding: PaddingValues,
    openTrip: (String) -> Unit,
    vm: MyTripsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("我建立的", "我參加的")

    Column(modifier = Modifier.padding(padding)) {
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
            }
        }

        when (val s = state) {
            is MyTripsUiState.Loading -> LoadingState(Modifier.fillMaxWidth())
            is MyTripsUiState.Error   -> ErrorState(Modifier.fillMaxWidth(), "載入失敗", s.message, onRetry = vm::retry)
            is MyTripsUiState.Empty   -> EmptyState(
                modifier = Modifier.fillMaxWidth(),
                title = if (selectedTab == 0) "還沒有建立任何行程" else "你還沒有加入任何行程",
                description = if (selectedTab == 0) "點擊右下角「＋」建立你的第一個行程" else "邀請朋友或加入他人分享的行程吧"
            )
            is MyTripsUiState.Data    -> {
                val list = if (selectedTab == 0) s.createdByMe else s.joinedByMe
                if (list.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth(),
                        title = if (selectedTab == 0) "還沒有建立任何行程" else "你還沒有加入任何行程",
                        description = if (selectedTab == 0) "點擊右下角「＋」建立你的第一個行程" else "邀請朋友或加入他人分享的行程吧"
                    )
                } else {
                    TripList(trips = list, openTrip = openTrip)
                }
            }
        }
    }
}