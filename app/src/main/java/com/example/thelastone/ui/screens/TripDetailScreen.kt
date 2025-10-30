package com.example.thelastone.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.data.model.Activity
import com.example.thelastone.data.model.Trip
import com.example.thelastone.ui.AlternativesDialog
import com.example.thelastone.ui.StartPreviewDialog
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.OpeningHoursSection
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.RatingSection
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.utils.buildOpenStatusTextFallback
import com.example.thelastone.utils.openNavigation
import com.example.thelastone.vm.StartFlowViewModel
import com.example.thelastone.vm.StartUiState
import com.example.thelastone.vm.TripDetailUiState
import com.example.thelastone.vm.TripDetailViewModel

@Composable
fun TripDetailScreen(
    padding: PaddingValues,
    viewModel: TripDetailViewModel = hiltViewModel(),
    startVm: StartFlowViewModel = hiltViewModel(),
    onAddActivity: (tripId: String) -> Unit = {},
    onEditActivity: (tripId: String, activityId: String) -> Unit = { _, _ -> },
    onDeleteActivity: (tripId: String, dayIndex: Int, activityIndex: Int, activity: Activity) -> Unit = { _,_,_,_ -> }
) {
    val state by viewModel.state.collectAsState()
    val perms = viewModel.perms.collectAsState().value
    val startState by startVm.ui.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        is TripDetailUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
        is TripDetailUiState.Error -> ErrorState(
            modifier = Modifier.padding(padding),
            message = s.message,
            onRetry = viewModel::reload
        )
        is TripDetailUiState.Data -> {
            val trip = s.trip
            var selected by rememberSaveable { mutableIntStateOf(0) }
            var sheetRef by remember { mutableStateOf<SheetRef?>(null) }

            // 內容列表
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item { TripInfoCard(trip) }
                            dayTabsAndActivities(
                                trip = trip,
                                selected = selected,
                                onSelect = { selected = it },
                                onActivityClick = { dayIdx, _, act ->
                                    val dayKey = trip.days[dayIdx].date
                                    sheetRef = SheetRef(dayKey, act.id)
                                }
                            )
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }

                    if (perms?.canEditTrip == true) {
                        FloatingActionButton(
                            onClick = { onAddActivity(trip.id) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.Filled.Add, null) }
                    }
                }
            }

            // 從當前 trip 狀態反查最新 activity
            val resolved = sheetRef?.let { trip.findActivity(it) }
            if (resolved == null && sheetRef != null) {
                // 這筆已經不存在 → 關閉
                LaunchedEffect(Unit) { sheetRef = null }
            }

            resolved?.let { (dayIdx, actIdx, act) ->
                ActivityBottomSheet(
                    activity = act,
                    readOnly = perms?.readOnly == true,
                    canEdit  = perms?.canEditTrip == true,
                    onDismiss = { sheetRef = null },
                    onEdit = {
                        onEditActivity(trip.id, act.id)
                        sheetRef = null
                    },
                    onDelete = {
                        onDeleteActivity(trip.id, dayIdx, actIdx, act)
                        sheetRef = null
                    },
                    onGoMaps = { openInMaps(context, act) },
                    onStart = { startVm.start(act.place) }
                )
            }

            // Start 流程也要用 sheetRef 現查
            when (val st = startState) {
                StartUiState.Idle -> Unit
                StartUiState.Loading -> {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("請稍候") },
                        text = { CircularProgressIndicator() },
                        confirmButton = {}
                    )
                }
                is StartUiState.Preview -> {
                    StartPreviewDialog(
                        info = st.info,
                        onDismiss = { startVm.reset() },
                        onConfirmDepart = {
                            val act = sheetRef?.let { trip.findActivity(it) }?.third
                            if (act != null) {
                                openNavigation(context, act.place.lat, act.place.lng, act.place.name)
                            }
                            startVm.reset()
                            sheetRef = null
                        },
                        onChangePlan = { startVm.showAlternatives() }
                    )
                }
                is StartUiState.Alternatives -> {
                    AlternativesDialog(
                        alts = st.alts,
                        onDismiss = { startVm.reset() },
                        onPick = { alt ->
                            val latest = sheetRef?.let { trip.findActivity(it) }
                            if (latest != null) {
                                onEditActivity(trip.id, latest.third.id) // 直接帶 ID 進編輯頁
                            }
                            startVm.reset()
                            sheetRef = null
                        },
                        onSeeMore = { startVm.loadMore() }
                    )
                }
                is StartUiState.Error -> {
                    AlertDialog(
                        onDismissRequest = { startVm.reset() },
                        title = { Text("發生錯誤") },
                        text = { Text(st.message) },
                        confirmButton = {
                            TextButton(onClick = { startVm.reset() }) { Text("關閉") }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityBottomSheet(
    activity: Activity,
    readOnly: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onGoMaps: () -> Unit,
    onStart: () -> Unit,
    note: String = "",
    onNoteChange: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var menuOpen by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp) // 由各區塊自行控距
        ) {
            // Header：店名 + 更多選單
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.place.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                if (canEdit) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("編輯") },
                                onClick = { menuOpen = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("刪除") },
                                onClick = {
                                    menuOpen = false
                                    showConfirm = true
                                }
                            )
                        }
                    }
                }
            }

            // Supporting text：地址（與標題距離更近）
            activity.place.address?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp)) // 關鍵：縮短與標題距離
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 時間 & Google 評分摘要（屬於 metadata，使用較淡層級）
            Spacer(Modifier.height(8.dp))
            val time = listOfNotNull(activity.startTime, activity.endTime)
                .takeIf { it.isNotEmpty() }?.joinToString(" ~ ") ?: "未設定時間"
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            val hasHours =
                !activity.place.openingHours.isNullOrEmpty() ||   // list 非空才算有
                        !activity.place.openStatusText.isNullOrBlank() || // 文字非空才算有
                        (activity.place.openNow != null)                  // 有給到 openNow 才算有


            if (hasHours) {
                Spacer(Modifier.height(4.dp))

                // 有資料才計算顯示文字：若沒有 openStatusText，才用 fallback
                val statusText = activity.place.openStatusText
                    ?: buildOpenStatusTextFallback(
                        activity.place.openNow,
                        activity.place.openingHours
                    )

                OpeningHoursSection(
                    hours = activity.place.openingHours,
                    statusText = statusText
                )
            }

            // ── 評分（有 rating 才顯示） ──
            activity.place.rating?.let { r ->
                RatingSection(
                    rating = r,
                    totalReviews = activity.place.userRatingsTotal ?: 0
                )
            }

            // 備註區：遵循表單與閱讀混合的 M3 風格
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium
            )

            // 行動按鈕列
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onGoMaps,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Go to Maps")
                }
                if (!readOnly) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    }

    // 確認刪除對話框
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("確認刪除") },
            text = { Text("你確定要刪除此活動嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onDelete()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun openInMaps(context: Context, activity: Activity) {
    val lat = activity.place.lat
    val lng = activity.place.lng
    val name = activity.place.name

    val uri = when {
        lat != null && lng != null ->
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
        else ->
            Uri.parse("geo:0,0?q=${Uri.encode(name)}")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps") // 若裝了 Google Maps 就優先用
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 沒有 Google Maps 時退回一般瀏覽器
        val web = if (lat != null && lng != null)
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        else
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(name)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, web))
    }
}

// 用來存當前選中的 Activity：用 dayKey + activityId 組合，避免 index 失效
data class SheetRef(val dayKey: String, val activityId: String)

// 從當前 trip 狀態，用 SheetRef 找到最新索引與物件
fun Trip.findActivity(ref: SheetRef): Triple<Int, Int, Activity>? {
    val dayIdx = days.indexOfFirst { it.date == ref.dayKey }
    if (dayIdx < 0) return null
    val actIdx = days[dayIdx].activities.indexOfFirst { it.id == ref.activityId }
    if (actIdx < 0) return null
    return Triple(dayIdx, actIdx, days[dayIdx].activities[actIdx])
}