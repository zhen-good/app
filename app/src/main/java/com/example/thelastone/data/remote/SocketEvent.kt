// data/remote/ChatWebSocketService.kt
package com.example.thelastone.data.remote
sealed class SocketEvent {
    object Connected : SocketEvent()
    object Disconnected : SocketEvent()
    data class NewMessage(val message: com.example.thelastone.data.remote.ChatMessage) : SocketEvent()
    data class SystemMessage(val message: String) : SocketEvent()
    data class UserJoined(val username: String, val message: String) : SocketEvent()
    data class UserLeft(val username: String, val message: String) : SocketEvent()
    data class UserTyping(val username: String, val isTyping: Boolean) : SocketEvent()
    data class Error(val message: String) : SocketEvent()

    /** 後端一般聊天訊息 */
    data class ChatMessage(val raw: String) : SocketEvent()

    /** 後端問答題（v2 版本） */
    data class AiQuestionV2(val rawJson: String) : SocketEvent()

    /** 後端問答題（舊版本） */
//    data class AiQuestionLegacy(val raw: String) : SocketEvent()

    /**分析的問題 */
    data class AiResponse(val rawJson: String) : SocketEvent() // 新增

}



