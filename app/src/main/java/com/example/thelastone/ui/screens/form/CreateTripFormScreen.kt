package com.example.thelastone.ui.screens.form

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.thelastone.data.model.AgeBand
import com.example.thelastone.data.model.TripVisibility
import com.example.thelastone.vm.TripFormViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTripFormScreen(
    padding: PaddingValues,
    onPreview: () -> Unit,
    viewModel: TripFormViewModel
) {
    val form by viewModel.form.collectAsState()

    var submitted by remember { mutableStateOf(false) }

    // Dialog 狀態
    var showDateRange by remember { mutableStateOf(false) }
    var showStartTime by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }

    // 驗證（包含 locations）
    val validationErrors = remember(form) {
        val errors = mutableMapOf<String, String>()

        if (form.name.isBlank()) {
            errors["name"] = "請輸入旅遊名稱"
        } else if (form.name.length > 50) {
            errors["name"] = "名稱最多 50 字"
        }

        // ✅ [新增] locations 的驗證
        if (form.locations.isBlank()) {
            errors["location"] = "請輸入旅遊地點"
        }

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val s = runCatching { LocalDate.parse(form.startDate, fmt) }.getOrNull()
        val e = runCatching { LocalDate.parse(form.endDate, fmt) }.getOrNull()
        when {
            s == null || e == null -> errors["date"] = "請選擇有效日期"
            e.isBefore(s) -> errors["date"] = "結束日期需晚於開始日期"
        }

        if ((form.activityStart != null) xor (form.activityEnd != null)) {
            errors["time"] = "活動時間需成對輸入"
        } else if (form.activityStart != null && form.activityEnd != null) {
            val tfmt = DateTimeFormatter.ofPattern("HH:mm")
            val ts = runCatching { LocalTime.parse(form.activityStart, tfmt) }.getOrNull()
            val te = runCatching { LocalTime.parse(form.activityEnd, tfmt) }.getOrNull()
            when {
                ts == null || te == null -> errors["time"] = "活動時間格式錯誤"
                !te.isAfter(ts) -> errors["time"] = "結束時間需晚於開始時間"
            }
        }
        errors
    }

    val nameErr = if (submitted) validationErrors["name"] else null
    val locErr = if (submitted) validationErrors["location"] else null // ✅ [新增]
    val dateErr = if (submitted) validationErrors["date"] else null
    val timeErr = if (submitted) validationErrors["time"] else null
    val allValid = validationErrors.isEmpty() && form.aiDisclaimerChecked

    // ---- UI ----
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp) // 給頂部一點空間
        ) {
            // 1) 旅遊名稱
            item {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("旅遊名稱（必填）") },
                    singleLine = true,
                    isError = nameErr != null,
                    supportingText = {
                        val count = "${form.name.length}/50"
                        Text(text = nameErr ?: count)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ✅ [新增] 2. 旅遊地點
            item {
                OutlinedTextField(
                    value = form.locations,
                    onValueChange = viewModel::updateLocations, // 👈 呼叫 ViewModel 更新
                    label = { Text("旅遊地點（必填）") },
                    placeholder = { Text("例如：台北、九份、台南") },
                    singleLine = true,
                    isError = locErr != null,
                    supportingText = {
                        val helperText = locErr ?: "請用地點名稱，並用逗號或空格分隔"
                        Text(text = helperText)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3) 可見性
            item {
                Column {
                    Text("可見性", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isPublic = form.visibility == TripVisibility.PUBLIC
                        FilterChip(
                            selected = isPublic,
                            onClick = { viewModel.setVisibility(TripVisibility.PUBLIC) },
                            label = { Text("公開") }
                        )
                        FilterChip(
                            selected = !isPublic,
                            onClick = { viewModel.setVisibility(TripVisibility.PRIVATE) },
                            label = { Text("私密") }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val tip = if (form.visibility == TripVisibility.PUBLIC)
                        "公開行程會出現在 Explore，所有人可瀏覽"
                    else
                        "私密行程僅你本人與成員可見"
                    Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 4) 日期區間
            item {
                Column {
                    Text("旅遊日期（必填）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = { showDateRange = true },
                        label = {
                            Text(
                                if (form.startDate.isBlank() || form.endDate.isBlank())
                                    "選擇日期"
                                else "${form.startDate} → ${form.endDate}"
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.DateRange, null) }
                    )
                    if (dateErr != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dateErr,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 5) 總預算
            item {
                OutlinedTextField(
                    value = form.totalBudget?.toString() ?: "",
                    onValueChange = viewModel::updateBudgetText,
                    label = { Text("總預算（新台幣，可留空）") },
                    singleLine = true,
                    prefix = { Text("NT$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("僅接受數字；不輸入表示未定") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 6) 活動時間
            item {
                Column {
                    Text("活動時間（選填）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { showStartTime = true },
                            label = { Text(form.activityStart ?: "開始時間") },
                            leadingIcon = { Icon(Icons.Filled.Schedule, null) }
                        )
                        AssistChip(
                            onClick = { showEndTime = true },
                            label = { Text(form.activityEnd ?: "結束時間") },
                            leadingIcon = { Icon(Icons.Filled.Schedule, null) }
                        )
                        Spacer(Modifier.weight(1f))
                        if (form.activityStart != null || form.activityEnd != null) {
                            TextButton(
                                onClick = {
                                    viewModel.updateActivityStart(null)
                                    viewModel.updateActivityEnd(null)
                                }
                            ) { Text("清除") }
                        }
                    }
                    if (timeErr != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            timeErr,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 7) 旅遊風格
            item {
                Column {
                    Text("旅遊風格（多選）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.styleOptions.forEach { s ->
                            FilterChip(
                                selected = form.styles.contains(s),
                                onClick = { viewModel.toggleStyle(s) },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }

            // 8) 偏好交通
            item {
                Column {
                    Text("偏好交通（多選）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.transportOptions.forEach { t ->
                            FilterChip(
                                selected = form.transportPreferences.contains(t),
                                onClick = { viewModel.toggleTransport(t) },
                                label = { Text(t) }
                            )
                        }
                    }
                }
            }

            // 9) 平均年齡
            item {
                Column {
                    Text("平均年齡（必選）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val all = listOf(
                            AgeBand.IGNORE to "不列入考量", AgeBand.UNDER_17 to "17以下",
                            AgeBand.A18_25 to "18-25", AgeBand.A26_35 to "26-35",
                            AgeBand.A36_45 to "36-45", AgeBand.A46_55 to "46-55",
                            AgeBand.A56_PLUS to "56以上"
                        )
                        all.forEach { (band, label) ->
                            FilterChip(
                                selected = form.avgAge == band,
                                onClick = { viewModel.setAvgAge(band) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 10) 參考 Google 評分
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("參考 Google 評分", style = MaterialTheme.typography.labelLarge)
                        Text("用較高評分做優先排序", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = form.useGmapsRating,
                        onCheckedChange = viewModel::setUseGmapsRating
                    )
                }
            }

            // 11) 其他需求
            item {
                OutlinedTextField(
                    value = form.extraNote ?: "",
                    onValueChange = viewModel::updateExtraNote,
                    label = { Text("其他需求（選填）") },
                    supportingText = { Text("例如：喜歡看夜景、想吃米其林餐廳…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            // 12) AI 提醒聲明
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = form.aiDisclaimerChecked,
                        onCheckedChange = viewModel::setAiDisclaimer
                    )
                    Text(
                        "行程建議由 AI 產生，僅供參考，並非完全精準，請自行調整。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (submitted && !form.aiDisclaimerChecked) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "請勾選此聲明以繼續",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) } // 讓底部按鈕有空間
        }

        // 底部按鈕
        Button(
            onClick = {
                submitted = true
                if (allValid) {
                    viewModel.generatePreview()
                    onPreview()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("預覽") }
    }

    // ===== Dialogs =====
    if (showDateRange) {
        val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
        val dateState = rememberDateRangePickerState()

        DatePickerDialog(
            onDismissRequest = { showDateRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val sMs = dateState.selectedStartDateMillis
                    val eMs = dateState.selectedEndDateMillis
                    if (sMs != null && eMs != null) {
                        val s = Instant.ofEpochMilli(sMs).atZone(ZoneId.systemDefault()).toLocalDate()
                        val e = Instant.ofEpochMilli(eMs).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.updateDateRange(s.format(formatter), e.format(formatter))
                    }
                    showDateRange = false
                }) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRange = false }) { Text("取消") }
            }
        ) {
            DateRangePicker(state = dateState)
        }
    }

    PlatformTimePickerDialog(
        show = showStartTime,
        initial = form.activityStart?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.now(),
        onDismiss = { showStartTime = false },
        onTimePicked = { picked -> viewModel.updateActivityStart(picked.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    )
    PlatformTimePickerDialog(
        show = showEndTime,
        initial = form.activityEnd?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.now(),
        onDismiss = { showEndTime = false },
        onTimePicked = { picked -> viewModel.updateActivityEnd(picked.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    )
}

@Composable
private fun PlatformTimePickerDialog(
    show: Boolean,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onTimePicked: (LocalTime) -> Unit,
    is24Hour: Boolean = true
) {
    if (!show) return
    val context = LocalContext.current
    // 使用 LaunchedEffect 確保 Dialog 只在 show 變為 true 時觸發一次
    LaunchedEffect(show) {
        TimePickerDialog(
            context,
            { _, h, m -> onTimePicked(LocalTime.of(h, m)) },
            initial.hour,
            initial.minute,
            is24Hour
        ).apply {
            setOnDismissListener { onDismiss() }
            show()
        }
    }
}

