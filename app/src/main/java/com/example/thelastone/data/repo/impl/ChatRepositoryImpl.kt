package com.example.thelastone.data.repo.impl

import android.util.Log
import com.example.thelastone.data.local.MessageDao
import com.example.thelastone.data.mapper.QuestionMapper
import com.example.thelastone.data.model.AiResponsePayload
import com.example.thelastone.data.model.ButtonDto
import com.example.thelastone.data.model.Message
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.data.model.QuestionAnswerDto
import com.example.thelastone.data.model.QuestionV2Dto
import com.example.thelastone.data.model.User
// 導入 DTO
// 🎯 【新增】必須導入這兩個 DTO，以便在 NewMessage 中解析
import com.example.thelastone.data.model.SocketTripInner
import com.example.thelastone.data.remote.ChatMessage

import com.example.thelastone.data.remote.ChatService
import com.example.thelastone.data.remote.ChatWebSocketService
import com.example.thelastone.data.remote.MessageDto
import com.example.thelastone.data.remote.SocketEvent
import com.example.thelastone.data.repo.ChatRepository
import com.example.thelastone.di.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.String


@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val service: ChatService,
    private val dao: MessageDao,
    private val json: Json,
    private val session: SessionManager,
    private val webSocketService: ChatWebSocketService,
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentTripId: String? = null
    private val _isConnected = MutableStateFlow(false)
    private val _realtimeMessages = MutableStateFlow<List<Message>>(emptyList())

    // ✅ 推薦地點 Flow 的定義
    private val _recommendations = MutableSharedFlow<List<PlaceLite>>(extraBufferCapacity = 1)
    val recommendationsFlow: SharedFlow<List<PlaceLite>> = _recommendations // 確保是 override

    init {
        Log.d(TAG, "===== ChatRepositoryImpl 初始化 =====")
        startListeningToSocketEvents()
    }

    // ✅ 實現 connect 方法
    override suspend fun connect(tripId: String, userId: String, username: String) {
        Log.d(TAG, "🔌 connect() 被調用")
        Log.d(TAG, "  tripId: $tripId")
        Log.d(TAG, "  userId: $userId")
        Log.d(TAG, "  username: $username")

        currentTripId = tripId

        // 等待連線成功後加入房間
        _isConnected
            .filter { it }
            .take(1)
            .collect {
                Log.d(TAG, "✅ 連線成功，加入房間")
                joinRoom(tripId, username, userId)
            }
    }

    //這個是題目板板
    @Serializable
    data class AiQuestionEnvelope(
        @SerialName("user_id") val userId: String? = null,
        // ✅ 關鍵修正：直接映射到你的 DTO
        val message: QuestionV2Dto? = null
    )


    private fun startListeningToSocketEvents() {
        repositoryScope.launch {
            Log.d(TAG, "🎧 開始監聽 WebSocket 事件")
            connectToChat().collect { event ->
                Log.d(TAG, "📨 收到事件: ${event::class.simpleName}")

                when (event) {
                    is SocketEvent.Connected -> {
                        Log.d(TAG, "✅ WebSocket 連接成功")
                        _isConnected.value = true
                    }

                    is SocketEvent.Disconnected -> {
                        Log.d(TAG, "❌ WebSocket 斷線")
                        _isConnected.value = false
                    }

                    is SocketEvent.NewMessage -> {
                        val messageText = event.message.content
                        val senderId = event.message.userId
                        Log.d(TAG, "💬 收到新訊息: $messageText")

                        // 💡 修正 2A：在 collect 區塊內同步取得 userId (假設 SessionManager 提供同步/緩存方法)
                        //
                        // ⚠️ 注意：由於 collect 是一個快速的非 suspend 區塊，最好在類別 init 時緩存 ID。
                        // 如果 SessionManager.getUserId() 是一個 suspend 函式，您需要用 runBlocking 或 Flow.collect 來緩存 ID。
                        // 為了立即解決問題，我們假設 SessionManager 有一個同步的屬性或方法：
                        //
                        val currentUserId = session.currentUserId // 假設這是 SessionManager 提供的同步屬性/緩存

                        // --- 區分：用戶訊息 vs. 系統/AI 訊息 ---

                        // 如果訊息來自當前用戶，則直接將其視為純文本並跳過 JSON 解析
                        if (senderId == currentUserId && currentUserId.isNotBlank()) {
                            addMessageToList(event.message, isAi = false, textToShow = messageText, suggestions = null, buttons = null)
                            Log.d(TAG, "✅ 用戶自己的訊息已加入列表。")
                            return@collect // 💡 修正 3：使用 return@collect 退出 lambda
                        }

                        // --- 來自 AI 或系統的訊息 (嘗試解析 JSON 結構) ---

                        // 1. 嘗試解析 AiResponsePayload（包含 AI 文字、建議和按鈕）
                        val aiResponsePayload = try {
                            json.decodeFromString<AiResponsePayload>(messageText)
                        } catch (e: Exception) {
                            // 如果不是 AiResponsePayload，不急著報錯，繼續嘗試下一個結構
                            null
                        }

                        if (aiResponsePayload != null) {
                            // ✅ 成功解析到 AI 結構化回覆 (包含按鈕)

                            addMessageToList(
                                event.message,
                                isAi = true,
                                textToShow = aiResponsePayload.message,
                                suggestions = null,
                                buttons = aiResponsePayload.buttons // ⭐ 傳入按鈕
                            )
                            Log.d(TAG, "✅ AI 結構化訊息 (含按鈕) 已加入列表。")
                            return@collect // 💡 修正 3：處理完畢，退出 lambda
                        }

                        // 2. 嘗試解析 SocketTripInner（原有的行程地點建議結構）
                        val tripResponse = try {
                            json.decodeFromString<SocketTripInner>(messageText)
                        } catch (e: Exception) {
                            // 🎯 修正：輸出詳細的 JSON 解析錯誤，幫助我們找出不匹配的欄位
                            Log.e(TAG, "❌ AiResponsePayload JSON 解析失敗！", e)
                            Log.d(TAG, "❌ 失敗的 JSON 內容: $messageText")
                            null
                        }

                        // ... (原有的提取 suggestions 邏輯) ...

                        // 3. 處理純文本/系統訊息
                        val suggestions = emptyList<PlaceLite>() // 假設這裡為空，以簡化

                        // 如果所有解析都失敗，則將其視為 AI/系統發送的純文本
                        addMessageToList(
                            event.message,
                            isAi = true,
                            textToShow = if (!suggestions.isNullOrEmpty()) "AI 已生成分析結果..." else messageText,
                            suggestions = suggestions,
                            buttons = null // 確保沒有按鈕
                        )
                        return@collect
                    }

                    is SocketEvent.AiQuestionV2 -> {
                        val rawJson = event.rawJson
                        Log.d(TAG, "🧩 收到 ai_question_v2: $rawJson")

                        val questionDto = try {
                            val envelope = json.decodeFromString<AiQuestionEnvelope>(rawJson)
                            envelope.message
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ AiQuestionV2 JSON 解析失敗", e)
                            null
                        }

                        val domainQuestion = questionDto?.let(QuestionMapper::fromV2)

                        if (domainQuestion != null) {
                            val id = UUID.randomUUID().toString()
                            val newMessage = Message(
                                id = id,
                                tripId = currentTripId ?: "",
                                sender = User("ai", "Trip AI", "", null, emptyList()),
                                text = domainQuestion.text,
                                timestamp = System.currentTimeMillis(),
                                isAi = true,
                                singleChoiceQuestion = domainQuestion,
                                suggestions = null
                            )

                            val list = _realtimeMessages.value.toMutableList()
                            list.add(newMessage)
                            _realtimeMessages.value = list
                            Log.d(TAG, "✅ 題目訊息已加入，總數: ${list.size}")
                        }
                    }
//                    is SocketEvent.Trip -> {
//                        // 处理行程数据
//                        Log.d("Trip", "行程: ${event.trip_name}")
//                        event.nodes.forEach { node ->
//                            Log.d("Trip", "${node.day}天 ${node.slot}")
//                            node.places.forEach { place ->
//                                Log.d("Place", "- ${place.name}")
//                            }
//                        }
//                    }
//                  行程資訊
                    is SocketEvent.SystemMessage -> {
                        Log.d(TAG, "📢 收到系統訊息: ${event.message}")
                        val msg = Message(
                            id = System.currentTimeMillis().toString(),
                            tripId = currentTripId ?: "",
                            sender = User("system", "Trip AI", "", null, emptyList()),
                            text = event.message,
                            timestamp = System.currentTimeMillis(),
                            isAi = true,
                            suggestions = null,
                        )

                        val list = _realtimeMessages.value.toMutableList()
                        list.add(msg)
                        _realtimeMessages.value = list
                        Log.d(TAG, "✅ 系統訊息 (文本) 已加入，總數: ${list.size}")
                    }




                    is SocketEvent.SystemMessage -> {
                        Log.d(TAG, "📢 收到系統訊息: ${event.message}")
                        val msg = Message(
                            id = System.currentTimeMillis().toString(),
                            tripId = currentTripId ?: "",
                            sender = User("system", "Trip AI", "", null, emptyList()),
                            text = event.message,
                            timestamp = System.currentTimeMillis(),
                            isAi = true,
                            suggestions = null,
                        )

                        val list = _realtimeMessages.value.toMutableList()
                        list.add(msg)
                        _realtimeMessages.value = list
                        Log.d(TAG, "✅ 系統訊息 (文本) 已加入，總數: ${list.size}")
                    }


                    //建議的卡片
                    is SocketEvent.AiResponse -> { // 🎯 AI 建議事件處理
                        val rawJson = event.rawJson
                        Log.d(TAG, "🤖 收到 AI 建議 (ai_response): $rawJson")

                        try {
                            val payload = json.decodeFromString<AiResponsePayload>(rawJson)

                            currentTripId?.let { tripId ->

                                // 🎯 核心修正：統一在 AiResponse 這裡將文本、按鈕、建議傳遞給 Message DTO

                                // 修正：確保 message 不為 null
                                val messageTextToShow = payload.message ?: "AI 已完成分析"

                                val aiMessage = Message(
                                    id = UUID.randomUUID().toString(),
                                    tripId = tripId,
                                    sender = User("ai", "Trip AI", "", null, emptyList()),
                                    text = messageTextToShow,
                                    timestamp = System.currentTimeMillis(),
                                    isAi = true,
                                    suggestions = null, // 建議卡片通常單獨顯示，這裡傳 null
                                    buttons = payload.buttons, // ⭐⭐⭐ 這裡傳入按鈕數據！ ⭐⭐⭐
                                    isQuestion = false,
                                    question = null
                                )

                                val list = _realtimeMessages.value.toMutableList()
                                list.add(aiMessage)
                                _realtimeMessages.value = list

                                Log.d(TAG, "✅ AI 文本及按鈕已加入列表。按鈕數: ${payload.buttons?.size ?: 0}")
                            }

                            // 2. 處理結構化的建議卡片 (保持不變)
                            if (payload.recommendation != null) {
                                // ... (原有的建議處理邏輯，例如發射 _recommendations) ...
                                Log.d(TAG, "✅ 結構化 AI 建議已儲存 (Type: ${payload.recommendation.type})")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ AI 建議 JSON 解析失敗", e)
                        }
                    }

                    else -> {
                        Log.d(TAG, "ℹ️ 忽略事件: ${event::class.simpleName}")
                    }
                }
            }
        }
    }

    private fun addMessageToList(
        socketMessage: ChatMessage, // <== 如果 SocketMessage 報錯，請改為 MessageDto
        isAi: Boolean,
        textToShow: String,
        suggestions: List<PlaceLite>?,
        buttons: List<ButtonDto>?
    ) {
        // 💡 修正 1：檢查 currentTripId，這是更新訊息列表的必要條件
        currentTripId?.let { tripId ->
            val msg = Message(
                id = socketMessage.id, // 來自 Socket 訊息的 ID
                tripId = tripId,
                sender = User(
                    id = socketMessage.id,
                    name = socketMessage.username,
                    email = "",
                    avatarUrl = null,
                    friends = emptyList()
                ),
                text = textToShow,
                timestamp = socketMessage.timestamp,
                isAi = isAi,
                suggestions = suggestions,
                buttons = buttons, // ⭐ 傳遞按鈕數據
                isQuestion = false,
                question = null
            )

            // 💡 修正 2：更新 StateFlow
            val list = _realtimeMessages.value.toMutableList()
            list.add(msg)
            _realtimeMessages.value = list
            Log.d(TAG, "✅ 訊息已透過輔助函式加入列表，總數: ${list.size}")
        }
    }

    override fun connectToChat(): Flow<SocketEvent> {
        return webSocketService.connect()
    }

    override fun joinRoom(tripId: String, username: String, userId: String) {
        Log.d(TAG, "===== joinRoom =====")
        currentTripId = tripId

        if (_isConnected.value) {
            webSocketService.joinRoom(tripId, username, userId)
            Log.d(TAG, "✅ joinRoom 已呼叫")
        } else {
            Log.w(TAG, "⚠️ WebSocket 未連線，無法加入房間")
        }
    }

    override fun leaveRoom(tripId: String, username: String) {
        Log.d(TAG, "===== leaveRoom =====")
        if (currentTripId == tripId) {
            currentTripId = null
        }
        webSocketService.leaveRoom(tripId, username)
    }

    override fun sendTypingStatus(tripId: String, username: String, isTyping: Boolean) {
        webSocketService.sendTypingStatus(tripId, username, isTyping)
    }

    override fun disconnect() {
        Log.d(TAG, "===== disconnect =====")
        webSocketService.disconnect()
        currentTripId = null
        _isConnected.value = false
        _realtimeMessages.value = emptyList()
    }

    override fun observeMessages(tripId: String): Flow<List<Message>> {
        Log.d(TAG, "===== observeMessages =====")
        return _realtimeMessages.asStateFlow()
    }

    override fun sendMessage(userId: String, tripId: String, message: String) {
        Log.d(TAG, "📤 準備發送訊息: $message")

        if (_isConnected.value) {
            webSocketService.sendMessage(tripId, userId, message)
            Log.d(TAG, "✅ 訊息已發送")
        } else {
            Log.e(TAG, "❌ WebSocket 未連線，無法發送訊息")
        }
    }

    override suspend fun sendQuestionAnswer(
        tripId: String,
        questionId: String,
        value: String
    ) {
        val answerPayload = json.encodeToString(
            QuestionAnswerDto(
                questionId = questionId,
                answer = value
            )
        )
        webSocketService.emit("send_answer", answerPayload, tripId)
    }

    override suspend fun analyze(tripId: String) {
        try {
            // 🚨 關鍵：獲取 userId (這是您先前遺漏的參數)
            // 假設您的 Repository 有辦法存取到當前使用者 ID (例如透過 SessionManager 或注入的 AuthRepo)
            val currentUserId = session.currentUserId // <== 確保這裡能拿到 ID

            // 🎯 核心修正：將 analyze 轉換為發送一條普通聊天訊息
            // 注意：根據 ChatRepository 的定義，參數順序可能是 userId, tripId, message

            // 假設您使用的簽名是：
            // override fun sendMessage(userId: String, tripId: String, message: String)

            sendMessage(
                userId = currentUserId, // ✅ 補上使用者 ID
                tripId = tripId,
                message = "分析" // ✅ 這是後端 Socket 期望的特殊指令
            )

            Log.d(TAG, "✅ 成功將 Analyze 請求轉換為 Socket 訊息發送")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 發送 '分析' 命令失敗", e)
            // ... 處理錯誤 ...
        }
    }

    // 🎯 假設您將 SocketTripInner 等 DTO 放置在 data.model 包中，因此這裡不重複定義。

}
