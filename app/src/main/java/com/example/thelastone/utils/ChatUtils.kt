package com.example.thelastone.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime

/** 使用者是否已經在清單底部（或接近底部） */
fun LazyListState.isAtBottom(threshold: Int = 1): Boolean {
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    val total = layoutInfo.totalItemsCount
    return total == 0 || lastVisible >= total - 1 - threshold
}

/** 監看鍵盤是否開啟（以 IME inset > 0 判斷） */
@Composable
fun rememberKeyboardOpen(): State<Boolean> {
    // 這裡是 @Composable 區域，安全
    val density = LocalDensity.current
    val ime = WindowInsets.ime

    // remember/derivedStateOf 內不要再呼叫任何 @Composable
    return remember(density) {
        derivedStateOf { ime.getBottom(density) > 0 }
    }
}