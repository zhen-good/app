package com.example.thelastone.di

import android.os.Build
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.socket.client.IO
import io.socket.client.Socket
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "NetworkModule"

    // 🔥 關鍵修改:根據裝置類型使用不同網址
    private fun getSocketUrl(): String {
        return if (isEmulator()) {
            "http://10.0.2.2:5000"  // 模擬器用這個
        } else {
            "http://172.20.10.4:5000"  // 實體手機用這個(你的電腦 IP)
        }
    }

    @Provides
    @Singleton
    fun provideSocket(): Socket {
        val socketUrl = getSocketUrl()
        Log.d(TAG, "🌐 Socket URL: $socketUrl")
        Log.d(TAG, "📱 裝置類型: ${if (isEmulator()) "模擬器" else "實體手機"}")

        val options = IO.Options().apply {
            // ✅ 修改:先用 polling,再用 websocket (Flask-SocketIO 建議)
            transports = arrayOf("polling", "websocket")

            reconnection = true
            reconnectionDelay = 1000
            reconnectionDelayMax = 5000
            reconnectionAttempts = Int.MAX_VALUE
            timeout = 20000

            // 加入這個設定
            forceNew = true
            path = "/socket.io/"
        }

        val socket = IO.socket(socketUrl, options)

        Log.d(TAG, "✅ Socket 實例已創建: ${System.identityHashCode(socket)}")

        socket.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "✅ WebSocket 已連線到: $socketUrl")
        }

        socket.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "❌ WebSocket 已斷線")
        }

        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.firstOrNull()
            Log.e(TAG, "❌ 連線錯誤: $error")
            Log.e(TAG, "   嘗試連線到: $socketUrl")
        }

        return socket
    }

    /**
     * 判斷是否為模擬器
     */
    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
}