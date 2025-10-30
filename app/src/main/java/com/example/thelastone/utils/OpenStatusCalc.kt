package com.example.thelastone.utils

// utils/OpenStatusCalc.kt
import com.example.thelastone.data.remote.ApiOpeningHours
import com.example.thelastone.data.remote.ApiPeriod
import java.time.format.DateTimeFormatter


data class OpenStatus(val openNow: Boolean, val text: String)

private val DAY_ORDER = listOf("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY")
private fun dayIndex(d: String?): Int = DAY_ORDER.indexOf(d?.uppercase()).coerceAtLeast(0)

private fun dayIndexFromEnum(day: Int?): Int? = when (day) {
    1 -> 0 // MONDAY
    2 -> 1
    3 -> 2
    4 -> 3
    5 -> 4
    6 -> 5
    7 -> 6 // SUNDAY
    else -> null // 0 或 null 視為未指定
}

private data class Window(val startMin: Int, val endMin: Int)
private fun toWindows(periods: List<ApiPeriod>?): List<Window> {
    if (periods.isNullOrEmpty()) return emptyList()
    val base = mutableListOf<Window>()

    for (p in periods) {
        val o = p.open
        val c = p.close
        val oDay = dayIndexFromEnum(o?.day)     ?: continue
        val oHour = o?.hour ?: continue
        val oMin  = o.minute ?: 0

        val start = oDay * 24 * 60 + oHour * 60 + oMin

        val end = if (c?.hour != null && c.minute != null && c.day != null) {
            val cDay = dayIndexFromEnum(c.day) ?: oDay
            cDay * 24 * 60 + c.hour * 60 + c.minute
        } else {
            // 沒 close → 視為到隔天同時（或 24h）
            start + 24 * 60
        }

        base += Window(start, end)
        // 跨週：end < start
        if (end < start) base += Window(start, end + 7 * 24 * 60)
    }

    // 複製一輪方便「下週」查詢
    return base + base.map { it.copy(startMin = it.startMin + 7*24*60, endMin = it.endMin + 7*24*60) }
}

// 以店家時區計算「本週分鐘」
private fun nowMinutesOfWeek(utcOffsetMinutes: Int, nowUtc: java.time.Instant = java.time.Instant.now()): Int {
    val zone = java.time.ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60)
    val ldt = java.time.ZonedDateTime.ofInstant(nowUtc, zone)
    val dowIdx = (ldt.dayOfWeek.value % 7) // 0..6
    return dowIdx * 24 * 60 + ldt.hour * 60 + ldt.minute
}

fun buildOpenStatus(
    current: ApiOpeningHours?,
    regular: ApiOpeningHours?,
    utcOffsetMinutes: Int
): OpenStatus? {
    current?.openNow?.let { on ->
        val windows = toWindows(current.periods ?: regular?.periods)
        val now = nowMinutesOfWeek(utcOffsetMinutes)
        if (on && windows.isNotEmpty()) {
            val inWin = windows.firstOrNull { now in it.startMin until it.endMin }
            val end = inWin?.endMin
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            val endText = end?.let { "%02d:%02d".format((it % (24*60))/60, (it % (24*60))%60) }
            return OpenStatus(true, endText?.let { "營業中 · 至 $it" } ?: "營業中")
        }
        if (!on) {
            val wins = windows.sortedBy { it.startMin }
            val next = wins.firstOrNull { it.startMin > now } ?: wins.firstOrNull()
            val start = next?.startMin
            val startText = start?.let { "%02d:%02d".format((it % (24*60))/60, (it % (24*60))%60) }
            return OpenStatus(false, startText?.let { "尚未營業 · $it 開始" } ?: "今日營業資訊缺漏")
        }
    }

    val windows = toWindows(current?.periods ?: regular?.periods)
    if (windows.isEmpty()) return null
    val now = nowMinutesOfWeek(utcOffsetMinutes)
    val inWin = windows.firstOrNull { now in it.startMin until it.endMin }
    return if (inWin != null) {
        val hm = inWin.endMin % (24*60)
        OpenStatus(true, "營業中 · 至 %02d:%02d".format(hm/60, hm%60))
    } else {
        val next = windows.firstOrNull { it.startMin > now } ?: windows.minByOrNull { it.startMin }
        val hm = next?.startMin?.rem(24*60) ?: return null
        OpenStatus(false, "尚未營業 · %02d:%02d 開始".format(hm/60, hm%60))
    }
}
