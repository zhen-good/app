package com.example.thelastone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.toLite
import com.example.thelastone.ui.screens.comp.PlaceCard
import com.example.thelastone.ui.screens.comp.placedetaildialog.PlaceDetailDialogHost
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.PlaceActionMode
import com.example.thelastone.ui.state.EmptyState
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.vm.SavedViewModel

@Composable
fun SavedScreen(
    padding: PaddingValues,
    openPlace: (String) -> Unit
) {
    val vm: SavedViewModel = hiltViewModel()
    val ui by vm.state.collectAsStateWithLifecycle()

    var preview by rememberSaveable { mutableStateOf<PlaceLite?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        when {
            ui.loading -> LoadingState(Modifier.fillMaxSize(), "載入收藏中…")
            ui.error != null -> ErrorState(
                modifier = Modifier.fillMaxSize(),
                message = ui.error!!,
                onRetry = { vm.refresh() } // ← 讓它真的重載
            )
            ui.items.isEmpty() -> EmptyState(Modifier.fillMaxSize(), "尚未收藏景點")
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = ui.items,
                        key = { it.id } // ← 穩定 key
                    ) { sp ->
                        val p = sp.place.toLite()
                        PlaceCard(
                            place = p,
                            onClick = { preview = p },
                            isSaved = true,
                            onToggleSave = { vm.toggle(p) }
                        )
                    }
                }
            }
        }
    }

    if (preview != null) {
        PlaceDetailDialogHost(
            place = preview,
            mode = PlaceActionMode.REMOVE_FROM_FAVORITE,
            onDismiss = { preview = null },
            onConfirmRight = {
                preview?.let { vm.toggle(it) }
                preview = null
            }
        )
    }
}
