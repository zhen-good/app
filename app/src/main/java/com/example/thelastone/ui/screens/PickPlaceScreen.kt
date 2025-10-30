package com.example.thelastone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.ui.screens.comp.PlaceCard
import com.example.thelastone.ui.screens.comp.placedetaildialog.PlaceDetailDialog
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.PlaceActionMode
import com.example.thelastone.ui.state.EmptyState
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.vm.ExploreViewModel
import com.example.thelastone.vm.SavedViewModel

@Composable
fun PickPlaceScreen(
    padding: PaddingValues,
    onSearchClick: () -> Unit,          // 進入全螢幕搜尋頁
    onPick: (PlaceLite) -> Unit         // 按「加入行程」後回傳
) {
    val tabs = listOf("Popular", "Saved")
    var selected by remember { mutableStateOf(0) }

    // ★ 收藏 VM（讓兩個分頁都能顯示愛心＋切換）
    val savedVm: SavedViewModel = hiltViewModel()
    val savedUi by savedVm.state.collectAsState()

    // ★ Dialog 狀態：目前挑選中的地點
    var preview by remember { mutableStateOf<PlaceLite?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // 搜尋欄（點擊跳搜尋頁）
        // 搜尋欄（整塊可點 → 進入搜尋頁）
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜尋景點、地址…") },
                trailingIcon = {
                    IconButton(onClick = onSearchClick) { Icon(Icons.Filled.Search, null) }
                }
            )

            // 透明點擊層：覆蓋整個 TextField
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        // 不要出現額外 ripple；也避免巢狀點擊衝突
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSearchClick() }
            )
        }



        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = selected == i,
                    onClick = { selected = i },
                    text = { Text(t) }
                )
            }
        }

        when (selected) {
            0 -> PopularTab(
                onCardClick = { preview = it },
                isSaved = { id -> savedUi.savedIds.contains(id) },
                onToggleSave = { savedVm.toggle(it) }
            )
            1 -> SavedTab(
                onCardClick = { preview = it },
                onToggleSave = { savedVm.toggle(it) }
            )
        }
    }

    // ★ 詳情 Dialog：按右下角「加入行程」→ 呼叫 onPick，並關閉
    if (preview != null) {
        PlaceDetailDialog(
            place = preview,
            mode = PlaceActionMode.ADD_TO_ITINERARY,
            onDismiss = { preview = null },
            onAddToItinerary = {
                preview?.let(onPick)
                preview = null
            }
        )
    }
}

@Composable
private fun PopularTab(
    onCardClick: (PlaceLite) -> Unit,
    isSaved: (String) -> Boolean,
    onToggleSave: (PlaceLite) -> Unit,
    vm: ExploreViewModel = hiltViewModel()
) {
    val ui by vm.state.collectAsState()
    when {
        ui.spotsLoading -> LoadingState(Modifier.fillMaxSize(), "載入中…")
        ui.spotsError != null -> ErrorState(
            Modifier.fillMaxSize(),
            ui.spotsError!!,
            onRetry = { vm.loadSpotsTaiwan() }   // ← 直接重載推薦清單
        )
        ui.spots.isEmpty() -> EmptyState(
            Modifier.fillMaxSize(),
            "目前找不到推薦景點"
        )
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.spots, key = { it.placeId }) { p ->
                    PlaceCard(
                        place = p,
                        onClick = { onCardClick(p) },
                        isSaved = isSaved(p.placeId),
                        onToggleSave = { onToggleSave(p) }
                    )
                }
            }
        }
    }
}


@Composable
private fun SavedTab(
    onCardClick: (PlaceLite) -> Unit,
    onToggleSave: (PlaceLite) -> Unit,
    vm: SavedViewModel = hiltViewModel()
) {
    val ui by vm.state.collectAsState()
    when {
        ui.loading -> LoadingState(Modifier.fillMaxSize(), "載入收藏中…")
        ui.error != null -> ErrorState(Modifier.fillMaxSize(), ui.error!!, onRetry = { })
        ui.items.isEmpty() -> EmptyState(Modifier.fillMaxSize(), "尚未收藏景點")
        else -> {
            // ✅ 用 LazyColumn，避免 Column+forEach 的回收/效能問題
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ui.items, key = { it.id }) { sp ->
                    val p = PlaceLite(
                        placeId = sp.place.placeId,
                        name = sp.place.name,
                        lat = sp.place.lat,
                        lng = sp.place.lng,
                        address = sp.place.address,
                        rating = sp.place.rating,
                        userRatingsTotal = sp.place.userRatingsTotal,
                        photoUrl = sp.place.photoUrl,
                        openingHours = sp.place.openingHours,
                        openNow = sp.place.openNow,                    // ✅ 帶上
                        openStatusText = sp.place.openStatusText       // ✅ 帶上
                    )
                    PlaceCard(
                        place = p,
                        onClick = { onCardClick(p) },
                        isSaved = true,
                        onToggleSave = { onToggleSave(p) }
                    )
                }
            }
        }
    }
}