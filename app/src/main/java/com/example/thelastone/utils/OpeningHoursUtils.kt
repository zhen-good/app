package com.example.thelastone.utils


import android.util.Log
import androidx.compose.material3.ColorScheme
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 純字串轉 24h（給展開清單用） ----------
fun normalizeTimeRange(raw: String): String = raw
    .replace("–", "-").replace("—", "-").replace("−", "-")
    .replace("~", "-").replace(" to ", "-")
    .replace(Regex("[\u2000-\u206F\u2E00-\u2E7F\\s]+"), " ")
    .replace(Regex("\\s*-\\s*"), "-")
    .trim()

fun parseTimeStringFlexible(input: String): java.time.LocalTime? {
    val clean = input.trim()
        .replace(".", "")
        .replace("–", "-")
        .replace("to", "-")
    val patterns = listOf("h:mm a","h a","hh:mm a","hh a","H:mm","HH:mm","H","HH")
    for (p in patterns) try {
        val f = DateTimeFormatter.ofPattern(p, Locale.ENGLISH)
        return java.time.LocalTime.parse(clean.uppercase(Locale.ENGLISH), f)
    } catch (_: Exception) {}
    return null
}

fun formatTimeRange24h(raw: String): String {
    val norm = normalizeTimeRange(raw)
    if (norm.equals("Closed", true)) return "休息"
    val lower = norm.lowercase()
    if (lower.contains("24") && lower.contains("hour")) return "24 小時營業"

    val parts = norm.split("-").map { it.trim() }
    if (parts.size != 2) return raw
    val open = parseTimeStringFlexible(parts[0])
    val close = parseTimeStringFlexible(parts[1])
    val out = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    return if (open != null && close != null) "${open.format(out)}-${close.format(out)}" else raw
}

// ---------- Fallback：只有在沒有 periods/openNow 時才用 ----------
data class OpeningStatusInfo(val text: String, val color: androidx.compose.ui.graphics.Color)

fun getOpeningStatusInfo(
    hours: List<String>,
    now: java.time.LocalTime,
    colorScheme: ColorScheme
): OpeningStatusInfo {
    val today = java.time.LocalDate.now().dayOfWeek.name.lowercase()
    val todayHours = hours.find { it.substringBefore(":").trim().lowercase() == today }
    val timeRange = todayHours?.split(":", limit = 2)?.getOrNull(1)?.let { normalizeTimeRange(it) }

    if (timeRange.isNullOrBlank()) return OpeningStatusInfo("今日營業資訊缺漏", colorScheme.onSurfaceVariant)
    if (timeRange.equals("Closed", true)) return OpeningStatusInfo("已打烊", colorScheme.primary)
    val normalized = timeRange.lowercase()
    if (normalized.contains("24") && normalized.contains("hour")) return OpeningStatusInfo("24 小時營業", colorScheme.primary)

    val parts = timeRange.split("-").map { it.trim() }
    if (parts.size != 2) {
        Log.e("OpeningHours", "時間格式錯誤：$timeRange")
        return OpeningStatusInfo("營業資訊錯誤", colorScheme.onSurfaceVariant)
    }

    val openRaw = parts[0]
    val closeRaw = parts[1]
    val closeTime = parseTimeStringFlexible(closeRaw)
    val openRawFinal = if (!openRaw.contains("AM", true) && !openRaw.contains("PM", true)) {
        val suffix = closeRaw.takeLastWhile { it != ' ' }.uppercase()
        "$openRaw $suffix"
    } else openRaw
    val openTime = parseTimeStringFlexible(openRawFinal)
    if (openTime == null || closeTime == null) {
        Log.e("OpeningHours", "時間解析失敗：$timeRange")
        return OpeningStatusInfo("營業資訊錯誤", colorScheme.onSurfaceVariant)
    }

    val out24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    return if (now.isAfter(openTime) && now.isBefore(closeTime)) {
        OpeningStatusInfo("營業中 · 至 ${closeTime.format(out24)}", colorScheme.primary)
    } else {
        OpeningStatusInfo("尚未營業 · ${openTime.format(out24)} 開始", colorScheme.primary)
    }
}

private fun mondayFirstTodayIndex(day: DayOfWeek) =
    if (day == DayOfWeek.SUNDAY) 6 else day.value - 1

/** 簡版 fallback：沒有 openStatusText 時，以 openNow + 今天描述湊一條 */
fun buildOpenStatusTextFallback(openNow: Boolean?, weekdayDescriptions: List<String>, zoneId: ZoneId = ZoneId.systemDefault()): String? {
    if (weekdayDescriptions.size < 7) return null
    val idx = mondayFirstTodayIndex(ZonedDateTime.now(zoneId).dayOfWeek)
    val today = weekdayDescriptions[idx]
    return when (openNow) {
        true  -> "營業中 · $today"
        false -> "休息中 · $today"
        null  -> today
    }
}