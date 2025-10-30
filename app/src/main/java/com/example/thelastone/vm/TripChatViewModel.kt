package com.example.thelastone.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thelastone.data.model.Message
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.repo.ChatRepository
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import com.example.thelastone.data.model.ChoiceOption
import com.example.thelastone.data.model.SingleChoiceQuestion
import com.example.thelastone.data.model.User
import java.util.UUID
import javax.inject.Inject


sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Data(
        val trip: Trip?,
        val messages: List<Message>,
        val input: String,
        val analyzing: Boolean,
        val showTripSheet: Boolean,
        val myId: String
    ) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

@HiltViewModel
class TripChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    tripRepo: TripRepository,
    private val session: SessionManager, // ✅ 加 private
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle["tripId"] ?: "test_trip_123"

    private val _input = MutableStateFlow("")
    private val _analyzing = MutableStateFlow(false)
    private val _showTripSheet = MutableStateFlow(false)

    private val myIdFlow: Flow<String> =
        session.auth.map { it?.user?.id ?: "guest" }

    private val tripFlow: Flow<Trip?> = flowOf(null)

    private val messagesFlow: Flow<List<Message>> =
        chatRepo.observeMessages(tripId)
            .catch { emit(emptyList()) }

    private data class UiBits(
        val trip: Trip?,
        val messages: List<Message>,
        val input: String,
        val analyzing: Boolean,
        val showTripSheet: Boolean
    )

    private val bitsFlow: Flow<UiBits> =
        combine(
            tripFlow,
            messagesFlow,
            _input,
            _analyzing,
            _showTripSheet
        ) { trip, msgs, input, analyzing, sheet ->
            UiBits(trip, msgs, input, analyzing, sheet)
        }

    val state: StateFlow<ChatUiState> =
        combine(bitsFlow, myIdFlow) { bits, myId ->
            ChatUiState.Data(
                trip = bits.trip,
                messages = bits.messages,
                input = bits.input,
                analyzing = bits.analyzing,
                showTripSheet = bits.showTripSheet,
                myId = myId
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState.Loading
        )

    init {
        Log.d("TripChatVM", "🚀 初始化 - tripId: $tripId")

        // ✅ 啟動 WebSocket 連接
        viewModelScope.launch {
            try {
                // 取得使用者資訊
                val auth = session.auth.first()
                val userId = auth?.user?.id ?: "guest"
                val username = auth?.user?.name ?: "Guest"

                Log.d("TripChatVM", "👤 使用者: $userId / $username")

                // ✅ 關鍵：連接並加入房間
                chatRepo.connect(
                    tripId = tripId,
                    userId = userId,
                    username = username
                )

                Log.d("TripChatVM", "✅ WebSocket 已連接並加入房間")

            } catch (e: Exception) {
                Log.e("TripChatVM", "❌ 連接失敗", e)
            }
        }
    }

    fun updateInput(v: String) {
        _input.value = v
    }

    // ✅ 修正 send() 方法
    fun send() = viewModelScope.launch {
        val txt = _input.value.trim()
        if (txt.isEmpty()) return@launch

        _input.value = ""

        try {
            // 取得當前使用者 ID
            val userId = session.auth.first()?.user?.id

            if (userId.isNullOrEmpty()) {
                Log.e("ChatVM", "❌ 使用者未登入")
                return@launch
            }

            // ✅ 使用新的 sendMessage 方法
            chatRepo.sendMessage(
                userId = userId,
                tripId = tripId,
                message = txt
            )

            Log.d("ChatVM", "✅ 訊息已發送: $txt")

        } catch (e: Exception) {
            Log.e("ChatVM", "❌ 發送失敗: ${e.message}", e)
        }
    }

    private var analyzeJob: Job? = null
    fun analyze() {
        if (_analyzing.value) return
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _analyzing.value = true
            try {
                chatRepo.analyze(tripId)
            } finally {
                _analyzing.value = false
            }
        }
    }

    fun toggleTripSheet(show: Boolean) {
        _showTripSheet.value = show
    }

    fun onSelectSuggestion(place: PlaceLite) = viewModelScope.launch {
        val userId = session.auth.first()?.user?.id
        if (userId.isNullOrEmpty()) {
            Log.e("ChatVM", "❌ 使用者未登入")
            return@launch
        }

        chatRepo.sendMessage(
            userId = userId,
            tripId = tripId,
            message = "選擇：${place.name}"
        )
    }
    // vm/TripChatViewModel.kt

    fun onSelectQuestionOption(question: SingleChoiceQuestion, option: ChoiceOption) {
        // 從當前狀態獲取使用者ID和選項文字
        val currentState = state.value
        val userId = (currentState as? ChatUiState.Data)?.myId
        val messageText = option.label

        if (userId.isNullOrEmpty()) {
            Log.e("ChatVM", "❌ 使用者 ID 為空，無法發送選項回覆")
            return
        }

        viewModelScope.launch {
            // 1. 發送答案給後端（後端處理題目邏輯）
            chatRepo.sendQuestionAnswer(
                tripId = tripId, // ⬅️ 這裡的錯誤！
                questionId = question.id,
                value = option.value ?: option.label
            )

            // 2. ✅ 關鍵：模擬使用者訊息，通過 Repository 發送到 Socket
            chatRepo.sendMessage(
                userId = userId,
                tripId = tripId,
                message = messageText // 使用選項的文字作為訊息內容
            )
        }
        // ❌ 刪除原本的 simulateUserMessage(question, option) 呼叫
    }
    // vm/TripChatViewModel.kt (新增以下程式碼)

    // 🎯 這是解決 Unresolved reference 'onButtonClick' 的關鍵！
    fun onButtonClick(buttonValue: String) = viewModelScope.launch {

        // 1. 取得使用者 ID
        val userId = session.auth.first()?.user?.id
        if (userId.isNullOrEmpty()) {
            Log.e("ChatVM", "❌ 使用者 ID 為空，無法發送按鈕回覆")
            return@launch
        }

        // 2. 將按鈕的值作為新的聊天訊息發送出去
        // 這是為了讓後端知道使用者選擇了哪個選項，並讓 UI 顯示使用者的選擇。
        try {
            chatRepo.sendMessage(
                userId = userId,
                tripId = tripId,
                message = buttonValue // 將按鈕的 value (例如 "1" 或 "略過") 作為訊息文本發送
            )
            Log.d("ChatVM", "✅ 按鈕值已發送: $buttonValue")

        } catch (e: Exception) {
            Log.e("ChatVM", "❌ 發送按鈕值失敗: ${e.message}", e)
        }
    }
}

//    private fun simulateUserMessage(question: SingleChoiceQuestion, option: ChoiceOption) {
//        val currentState = state.value
//        if (currentState !is ChatUiState.Data) return
//
//        val myId = currentState.myId
//        val tripId = currentState.trip.id
//        // 假設您在 ChatUiState.Data 中儲存了使用者的名稱
//        val myUsername = currentState.myId // 請確保這個屬性存在
//
//        // 構建顯示給使用者的文字 (例如："悠閒漫遊")
//        val userText = option.label
//
//        // 創建一個新的 Message 物件，標記為 isAi = false (使用者訊息)
//        val userResponse = Message(
//            id = UUID.randomUUID().toString(),
//            tripId = tripId,
//            sender = User(
//                id = myId,
//                name = myUsername,
//                email = "",
//                avatarUrl = null,
//                friends = emptyList()
//            ),
//            text = userText,
//            timestamp = System.currentTimeMillis(),
//            isAi = false, // 關鍵：標記為使用者訊息
//            suggestions = null,
//            singleChoiceQuestion = nullTripChatViewModel
//        )
//
//        // 將這個模擬訊息加入到狀態流中
//        _state.update {
//            if (it is ChatUiState.Data) {
//                it.copy(messages = it.messages + userResponse)
//            } else it
//        }
//    }

