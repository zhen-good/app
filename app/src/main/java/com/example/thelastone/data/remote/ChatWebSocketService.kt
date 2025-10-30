package com.example.thelastone.data.remote

import android.util.Log
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ChatWebSocketService @Inject constructor(
    private val socket: Socket  // ✅ 注入同一個 Socket
) {
    companion object {
        private const val TAG = "ChatWebSocket"
    }
    private val commonHandler: Emitter.Listener = Emitter.Listener { args ->
        // 這裡放處理 WebSocket 事件的實際邏輯
        // 例如： Log.d(TAG, "Socket Event Received: ${args.joinToString()}")
    }

    init {
        Log.d(TAG, "🏭 ChatWebSocketService 初始化")
        Log.d(TAG, "🏭 Socket 實例 ID: ${System.identityHashCode(socket)}")
    }


    private fun Socket.onAny(function: Any) {}

    fun emit(event: String, data: String, room: String) {
        // 假設 socket 是 Socket.IO 客戶端
        socket.emit(event, data, room)
    }


    fun connect(): Flow<SocketEvent> = callbackFlow {

        // ✅ 修正 1：將 commonHandler 提升到 callbackFlow 頂部
        // 這樣 awaitClose 才能存取它
        val commonHandler = io.socket.emitter.Emitter.Listener { args ->
            val payload = args.joinToString { it.toString() }
            Log.d(TAG, "[onAny] 收到事件 payload=$payload")
        }

        // ✅ 修正 2：將 events 列表提升到 callbackFlow 頂部
        val events = listOf("chat_message", "ai_question_v2", "ai_response", "joined","trip")


        try {
            // 註冊所有你想看的事件
            // 現在可以存取外部的 events 列表
            for (evt in events) {
                socket.on(evt, commonHandler)
            }

            Log.d(TAG, "✅ 已註冊事件攔截器: ${events.joinToString()}")

            Log.d(TAG, "🚀 設定 Socket 監聽器")
            Log.d(TAG, "🔌 Socket 連線狀態: ${socket.connected()}")

            // ... (EVENT_CONNECT, EVENT_DISCONNECT, EVENT_CONNECT_ERROR 監聽器保持不變)
            socket.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "✅ Socket 已連線")
                trySend(SocketEvent.Connected)
            }

            socket.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "❌ Socket 已斷線")
                trySend(SocketEvent.Disconnected)
            }

            socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
                Log.e(TAG, "❌ 連線錯誤: $error")
                trySend(SocketEvent.Error(error))
            }


            // 監聽 chat_message 事件
            socket.on("chat_message") { args ->
                // ... (chat_message 處理邏輯)
                try {
                    Log.d(TAG, "📨 收到 chat_message")
                    val data = args[0] as JSONObject
                    Log.d(TAG, "📨 資料: $data")

                    val userId = data.getString("user_id")
                    val messageText = data.getString("message")

                    if (userId == "系統") {
                        Log.d(TAG, "📢 系統訊息: $messageText")
                        trySend(SocketEvent.SystemMessage(messageText))
                    } else {
                        val message = ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            content = messageText,
                            username = userId,
                            userId = userId,
                            timestamp = System.currentTimeMillis()
                        )
                        Log.d(TAG, "✅ 用戶訊息: ${message.content}")
                        trySend(SocketEvent.NewMessage(message))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析 chat_message 失敗", e)
                }
            }

            //監聽題目
            socket.on("ai_question_v2") { args ->
                val raw = args.firstOrNull()?.toString() ?: return@on
                Log.d(TAG, "🧩 收到 ai_question_v2: $raw")
                trySend(SocketEvent.AiQuestionV2(raw))
            }

            // 監聽 trip 事件
            // 假設這段程式碼位於您的 ChatRepositoryImpl 或 SocketRepositoryImpl 內部

            socket.on("trip") { args -> // 🎯 將監聽的事件名稱改為 "trip"
                // ... (保持內部處理邏輯不變)
                try {
                    Log.d(TAG, "📨 收到 trip 事件 (行程文本回覆)") // 修正日誌訊息
                    val data = args[0] as JSONObject
                    Log.d(TAG, "📨 資料: $data")

                    val userId = data.getString("user_id")
                    val messageText = data.getString("message")

                    // 由於後端傳送的 user_id 是 "系統"，所以會觸發 SystemMessage
                    if (userId == "系統") {
                        Log.d(TAG, "📢 系統訊息 (行程): $messageText")
                        trySend(SocketEvent.SystemMessage(messageText)) // 前端會將其渲染為 AI 訊息
                    } else {
                        // ... (如果是非系統用戶訊息，處理邏輯不變)
                        val message = ChatMessage(
                            id = System.currentTimeMillis().toString(),
                            content = messageText,
                            username = userId,
                            userId = userId,
                            timestamp = System.currentTimeMillis()
                        )
                        Log.d(TAG, "✅ 用戶訊息: ${message.content}")
                        trySend(SocketEvent.NewMessage(message))
                    }
                } catch (e: Exception) {
                    // 修正日誌訊息
                    Log.e(TAG, "❌ 解析 trip 事件內容失敗", e)
                }
            }

            // 🎯 新增：監聽 AI 建議的 ai_response 事件
            socket.on("ai_response") { args ->
                val raw = args.firstOrNull()?.toString() ?: return@on
                Log.d(TAG, "🤖 收到 ai_response (AI 建議): $raw")

                // 必須使用我們之前建議的 SocketEvent.AiResponse(rawJson: String)
                // 將原始 JSON 傳遞給上層 Repository 進行解析
                try {
                    // 確保 SocketEvent.AiResponse 已經定義在 data.remote.SocketEvent 中
                    trySend(SocketEvent.AiResponse(raw))
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 處理 ai_response 失敗", e)
                }
            }

            // ✅ 如果還沒連線，開始連線
            if (!socket.connected()) {
                Log.d(TAG, "開始連線...")
                socket.connect()
            } else {
                Log.d(TAG, "Socket 已連線，直接發送 Connected 事件")
                trySend(SocketEvent.Connected)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失敗", e)
            trySend(SocketEvent.Error(e.message ?: "Unknown error"))
        }



        awaitClose {
            Log.d(TAG, "⚠️ Flow 關閉，移除監聽器")
            socket.off(Socket.EVENT_CONNECT)
            socket.off(Socket.EVENT_DISCONNECT)
            socket.off(Socket.EVENT_CONNECT_ERROR)

            // 移除所有專屬監聽器
            socket.off("chat_message")
            socket.off("ai_question_v2") // <-- 現在可以存取
            socket.off("trip")

            // 移除 commonHandler 註冊的所有事件
            // 現在可以存取 commonHandler 和 events 列表
            for (evt in events) {
                socket.off(evt, commonHandler)
            }
        }
    }


    fun joinRoom(tripId: String, username: String, userId: String) {
        Log.d(TAG, "👋 發送 join 事件")
        Log.d(TAG, "  tripId: $tripId")
        Log.d(TAG, "  userId: $userId")
        Log.d(TAG, "  name: $username")
        Log.d(TAG, "  Socket 連線: ${socket.connected()}")

        val data = JSONObject().apply {
            put("trip_id", tripId)
            put("user_id", userId)
            put("name", username)
        }

        socket.emit("join", data)
        Log.d(TAG, "✅ join 事件已發送")
    }

    fun leaveRoom(tripId: String, username: String) {
        socket.emit("leave", JSONObject().apply {
            put("trip_id", tripId)
            put("user_id", username)
        })
    }

    fun sendMessage(tripId: String, userId: String, message: String) {
        Log.d(TAG, "📤 發送訊息: $message")
        Log.d(TAG, "  Socket 連線: ${socket.connected()}")

        if (!socket.connected()) {
            Log.e(TAG, "❌ Socket 未連線，無法發送")
            return
        }

        val data = JSONObject().apply {
            put("trip_id", tripId)
            put("user_id", userId)
            put("message", message)
        }

        socket.emit("user_message", data)
        Log.d(TAG, "✅ user_message 事件已發送")
    }

    fun sendTypingStatus(tripId: String, username: String, isTyping: Boolean) {
        // 實作打字狀態
    }

    fun disconnect() {
        socket.disconnect()
    }
}

data class ChatMessage(
    val id: String,
    val content: String,
    val username: String,
    val userId: String,
    val timestamp: Long
)