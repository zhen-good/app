package com.example.thelastone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.thelastone.data.model.ChoiceOption
import com.example.thelastone.data.model.Message
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.SingleChoiceQuestion
import com.example.thelastone.data.model.Trip
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.utils.isAtBottom
import com.example.thelastone.utils.rememberKeyboardOpen
import com.example.thelastone.vm.ChatUiState
import com.example.thelastone.vm.TripChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripChatScreen(
    padding: PaddingValues,
    viewModel: TripChatViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsState()

    when (val st = s) {
        is ChatUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
        is ChatUiState.Error   -> ErrorState(modifier = Modifier.fillMaxSize().padding(padding), message = st.message, onRetry = {})
        is ChatUiState.Data -> {
            if (st.showTripSheet) {
                TripSheet(trip = st.trip, onDismiss = { viewModel.toggleTripSheet(false) })
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)              // ← 關鍵：只吃 Scaffold 的 padding
            ) {
                // 訊息清單
                MessagesList(
                    modifier = Modifier.weight(1f),
                    messages = st.messages,
                    myId = st.myId,
                    onSelectSuggestion = viewModel::onSelectSuggestion,
                    onSelectQuestionOption = viewModel::onSelectQuestionOption,
                    onButtonClick = viewModel::onButtonClick
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { viewModel.analyze() },
                        enabled = !st.analyzing,
                        label = { Text(if (st.analyzing) "分析中…" else "分析") },
                        leadingIcon = { Icon(Icons.Default.TipsAndUpdates, null) }
                    )
                    AssistChip(
                        onClick = { viewModel.toggleTripSheet(true) },
                        label = { Text("行程") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null) }
                    )
                }

                // 輸入列：加上 imePadding 讓鍵盤頂起來時不被遮住
                ChatInputBar(
                    value = st.input,
                    onValueChange = viewModel::updateInput,
                    onSend = viewModel::send,
                    modifier = Modifier
                        .imePadding()               // ← 鍵盤彈出時自動避讓
                        .navigationBarsPadding()    // ← 全螢幕下靠近底部也能避開
                )
            }
        }
    }
}

@Composable
private fun MessagesList(
    modifier: Modifier = Modifier,
    messages: List<Message>,
    myId: String,
    onSelectSuggestion: (PlaceLite) -> Unit,
    // ✅ 修正：新增 onSelectQuestionOption 參數
    onSelectQuestionOption: (SingleChoiceQuestion, ChoiceOption) -> Unit,
    onButtonClick: (String) -> Unit
    // 記得要先定義 ChoiceOption, SingleChoiceQuestion
    // 並且在 TripChatScreen 呼叫 MessagesList 時要傳入這個參數
) {
    val listState = rememberLazyListState()
    val keyboardOpen by rememberKeyboardOpen()


//這是在讓頁面捲到最底
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(messages.size, keyboardOpen) {
        if (messages.isNotEmpty() && listState.isAtBottom()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            val isAi = msg.isAi
            val isMine = !isAi && msg.sender.id == myId

            val bubbleColor =
                when {
                    isAi   -> MaterialTheme.colorScheme.surfaceVariant
                    isMine -> MaterialTheme.colorScheme.primaryContainer
                    else   -> MaterialTheme.colorScheme.surface
                }

            when {
                // --- Trip AI：整個框置中（內文不變，不做置中） ---
                isAi -> {
                    Box(Modifier.fillMaxWidth()) {
                        Surface(
                            // ... (Surface 的屬性保持不變) ...
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                // 1. 顯示發送者名稱
                                Text(
                                    text = "Trip AI",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))

                                // 2. 顯示訊息文本 (msg.text)
                                Text(msg.text)

                                // 🎯 核心修正：檢查並渲染按鈕 (在文本之後)
                                if (msg.buttons.isNullOrEmpty().not()) {
                                    Spacer(Modifier.height(12.dp)) // 文本與按鈕間距

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp) // 按鈕間距
                                    ) {
                                        msg.buttons!!.forEach { buttonDto ->
                                            Button(
                                                onClick = { onButtonClick(buttonDto.value) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = MaterialTheme.shapes.small // 與建議卡片形狀保持一致
                                            ) {
                                                Text(buttonDto.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp)) // 按鈕與後續內容間距
                                }

                                // 3. 渲染建議卡片 (原有的邏輯)
                                val sug = msg.suggestions
                                if (!sug.isNullOrEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        sug.forEach { p ->
                                            SuggestionCard(place = p, onClick = { onSelectSuggestion(p) })
                                        }
                                    }
                                }

                                // 4. 渲染題目卡片 (原有的邏輯)
                                val question = msg.singleChoiceQuestion
                                if (question != null) {
                                    Spacer(Modifier.height(8.dp))
                                    AiQuestionCard(
                                        question = question,
                                        onSelectOption = onSelectQuestionOption
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 我自己：整塊靠右；"You" 在泡泡外、右上 ---
                isMine -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            // 泡泡外右上角的標籤
                            Text(
                                text = "You",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 2.dp, bottom = 4.dp)
                            )

                            // 泡泡本體
                            Surface(
                                tonalElevation = 0.dp,
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .wrapContentWidth()           // ← 寬度依內容
                                    .widthIn(max = 320.dp)        // ← 最多 320dp，避免太長
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        msg.text,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                        }
                    }
                }
                // --- 其他人：維持靠左；名稱仍在泡泡內（不變） ---
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            tonalElevation = 0.dp,
                            shape = MaterialTheme.shapes.medium,
                            color = bubbleColor,
                            modifier = Modifier
                                .wrapContentWidth()
                                .widthIn(max = 320.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.sender.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(msg.text)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SuggestionCard(
    place: PlaceLite,//這邊是我傳什麼資料進去
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                place.address?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            place.rating?.let {
                Text("★ $it", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AiQuestionCard(
    question: SingleChoiceQuestion,
    onSelectOption: (SingleChoiceQuestion, ChoiceOption) -> Unit
) {
    // 渲染題目文本（可以選擇在主 Text(msg.text) 中顯示，這裡則只顯示按鈕）
    // Text(question.text, style = MaterialTheme.typography.bodyLarge)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.options.forEach { option ->
            Button(
                onClick = { onSelectOption(question, option) },
                modifier = Modifier.fillMaxWidth(),
                // 將按鈕樣式調整為與背景色區隔，以確保看得見
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                // 左對齊標籤
                Text(
                    option.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("輸入訊息…") },
            singleLine = true
        )
        Button(onClick = onSend) { Text("送出") }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSheet(
    trip: Trip?,
    onDismiss: () -> Unit
) {
    if (trip == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(trip.name, style = MaterialTheme.typography.titleLarge)
            Text("${trip.startDate} ~ ${trip.endDate}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            trip.days.forEachIndexed { idx, day ->
                Text("Day ${idx + 1} - ${day.date}", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.activities.forEach { act ->
                        Text("• ${act.startTime} ~ ${act.endTime}  ${act.place.name}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}