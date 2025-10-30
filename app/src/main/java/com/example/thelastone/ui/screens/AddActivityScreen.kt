package com.example.thelastone.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.thelastone.ui.navigation.TripRoutes
import com.example.thelastone.ui.state.EmptyState
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.utils.findDayIndexByDate
import com.example.thelastone.utils.millisToDateString
import com.example.thelastone.vm.AddActivityUiState
import com.example.thelastone.vm.AddActivityViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityScreen(
    padding: PaddingValues,
    tripId: String,
    placeJson: String?,
    activityId: String? = null,
    nav: NavHostController
) {
    val vm: AddActivityViewModel = hiltViewModel()

    // 既有：依參數載入
    LaunchedEffect(activityId, placeJson) {
        if (!activityId.isNullOrBlank()) {
            vm.loadForEdit(tripId, activityId)
        } else if (!placeJson.isNullOrBlank()) {
            vm.initForCreate(tripId, placeJson)
        } else {
            vm.fail("缺少必要參數")
        }
    }

    // ✅ 這段是關鍵：收集 VM 的單次事件並導航
    LaunchedEffect(Unit) {
        vm.effects.collectLatest { eff ->
            when (eff) {
                is AddActivityViewModel.Effect.NavigateToDetail -> {
                    // 方案 A：嘗試直接彈回到既有的 Detail（若在返回堆疊上）
                    val popped = nav.popBackStack(TripRoutes.Detail, inclusive = false)
                    if (!popped) {
                        // 方案 B：不在堆疊上就重新導航到該 trip 的 Detail
                        nav.navigate(TripRoutes.detail(eff.tripId)) {
                            // 清掉舊的 Detail（路由樣板可用）
                            popUpTo(TripRoutes.Detail) { inclusive = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val canSubmit = remember(state) { state.trip != null && !state.submitting }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Button(
                onClick = vm::submit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (vm.editing) "儲存變更" else "提交新增")
            }
        }
    ) { inner ->

        // ✅ 內容只吃 inner + 自己的水平間距
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(inner)
            .padding(horizontal = 16.dp)

        when (val p = state.phase) {
            AddActivityUiState.Phase.Loading -> {
                LoadingState(modifier = contentModifier)
            }
            is AddActivityUiState.Phase.Error -> {
                ErrorState(
                    modifier = contentModifier,
                    title = "載入失敗",
                    message = p.message,
                    retryLabel = "重試",
                    onRetry = vm::reload,
                    secondaryLabel = "返回",
                    onSecondary = { nav.navigateUp() }
                )
            }
            AddActivityUiState.Phase.Ready -> {
                AddActivityForm(
                    modifier = contentModifier,
                    state = state,
                    onDateChange = vm::updateDate,
                    onStartTimeChange = vm::updateStartTime,
                    onEndTimeChange = vm::updateEndTime,
                    onNoteChange = vm::updateNote
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddActivityForm(
    modifier: Modifier = Modifier,
    state: AddActivityUiState,
    onDateChange: (Long?) -> Unit,
    onStartTimeChange: (String?) -> Unit,
    onEndTimeChange: (String?) -> Unit,
    onNoteChange: (String?) -> Unit
) {
    val trip = state.trip ?: run {
        EmptyState(
            modifier = modifier,
            title = "尚未載入行程",
            description = "請稍候或重試一次。"
        )
        return
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.selectedDateMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = millisToDateString(utcTimeMillis)
                return findDayIndexByDate(trip, d) != null
            }
        }
    )

    LaunchedEffect(datePickerState.selectedDateMillis) {
        onDateChange(datePickerState.selectedDateMillis)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(state.place?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text("${trip.startDate} ~ ${trip.endDate}")
        }
        // 📅 日期（OutlinedTextField 風格＋下拉展開動畫）
        item {
            DateFieldExpandable(
                label = "日期",
                datePickerState = datePickerState,
                onDateChange = onDateChange
            )
        }

        // ⏰ 開始時間（平台 TimePicker）
        item {
            TimeFieldPlatformDialog(
                label = "開始時間",
                value = state.startTime,
                onChange = onStartTimeChange
            )
        }

        // ⏰ 結束時間（平台 TimePicker）
        item {
            TimeFieldPlatformDialog(
                label = "結束時間",
                value = state.endTime,
                onChange = onEndTimeChange
            )
        }

        item {
            OutlinedTextField(
                value = state.note ?: "",
                onValueChange = { onNoteChange(it.ifBlank { null }) },
                label = { Text("備註") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------- 可重用 TimeField ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldExpandable(
    label: String,
    datePickerState: androidx.compose.material3.DatePickerState,
    onDateChange: (Long?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    // 顯示用文字
    val selectedText = datePickerState.selectedDateMillis
        ?.let { millisToDateString(it) } ?: "未選擇"

    // 展開圖示旋轉動畫
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrowRotation"
    )

    val interaction = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header：看起來就像 OutlinedTextField（disabled + 外層接點擊）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                enabled = false, // 讓外層接收點擊
                readOnly = true,
                label = { Text(label) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // 展開內容：維持「Outlined」視覺（加邊框、同圓角），用下拉動畫顯示
        AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit  = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                // DatePicker 本體
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp) // 與外框留點距離，跟 TextField 的 padding 感覺一致
                )
            }
        }
    }

    // 對外同步所選日期
    LaunchedEffect(datePickerState.selectedDateMillis) {
        onDateChange(datePickerState.selectedDateMillis)
    }
}

@Composable
private fun TimeFieldPlatformDialog(
    label: String,
    value: String?,                  // "HH:mm" 或 null
    onChange: (String?) -> Unit,
    use24Hour: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 是否顯示平台 Dialog（用副作用觸發）
    var show by remember { mutableStateOf(false) }

    // 點擊當下帶入的初始時分
    var pendingHour by remember { mutableStateOf(0) }
    var pendingMinute by remember { mutableStateOf(0) }

    val interaction = remember { MutableInteractionSource() }

    // 外層負責點擊；TextField 設 disabled 讓事件不被吃掉
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                val (h, m) = parseTimeOrNow(value)
                pendingHour = h
                pendingMinute = m
                show = true
            }
    ) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},          // 僅用挑選器
            enabled = false,             // 關鍵：讓點擊由外層接手
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // 把 disabled 樣式調成「看起來像啟用」
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (show) {
        LaunchedEffect(show, pendingHour, pendingMinute, use24Hour) {
            val dlg = TimePickerDialog(
                context,
                { _, h, m ->
                    onChange(formatTime(h, m))
                    show = false
                },
                pendingHour,
                pendingMinute,
                use24Hour
            )
            dlg.setOnCancelListener { show = false }
            dlg.setOnDismissListener { show = false }
            dlg.show()
        }
    }
}

/* ---------- 小工具函式 ---------- */

private fun parseTimeOrNow(text: String?): Pair<Int, Int> = try {
    if (text.isNullOrBlank()) {
        val now = java.time.LocalTime.now()
        now.hour to now.minute
    } else {
        val (h, m) = text.split(":")
        h.toInt().coerceIn(0, 23) to m.toInt().coerceIn(0, 59)
    }
} catch (_: Exception) {
    val now = java.time.LocalTime.now()
    now.hour to now.minute
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)