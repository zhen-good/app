package com.example.thelastone.data.repo.impl

import com.example.thelastone.data.model.*
import com.example.thelastone.data.repo.StartRepository
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.random.Random

class StartRepositoryFake : StartRepository {

    override suspend fun getStartInfo(place: Place): StartInfo {
        // 模擬網路延遲
        delay(450)

        // 假天氣（依 placeId 做 deterministic 隨機）
        val seed = place.placeId.hashCode().absoluteValue
        val rnd = Random(seed)
        val temp = 24 + rnd.nextInt(10)      // 24~33
        val rain = listOf(null, 10, 20, 30, 40, 50).random(rnd)
        val summary = listOf("晴朗", "多雲", "多雲時晴", "午後雷陣雨", "陰時雨").random(rnd)

        val openNow = listOf(true, false, null).random(rnd)
        val status = when (openNow) {
            true -> "營業中 · 至 21:00"
            false -> "已打烊 · 明天 10:00 開"
            null -> null
        }

        val hours = listOf(
            "週一 10:00–21:00", "週二 10:00–21:00", "週三 10:00–21:00",
            "週四 10:00–21:00", "週五 10:00–22:00", "週六 10:00–22:00", "週日 10:00–20:00"
        )

        val alt = fakeAlts(place.placeId, page = 0, rnd = rnd)

        return StartInfo(
            placeId = place.placeId,
            weather = WeatherInfo(summary = summary, temperatureC = temp, rainProbability = rain),
            openNow = openNow,
            openStatusText = status,
            openingHours = hours,
            alternatives = alt,
            page = 0
        )
    }

    override suspend fun getAlternatives(placeId: String, page: Int): List<Alternative> {
        delay(350)
        val rnd = Random(placeId.hashCode().absoluteValue + page)
        return fakeAlts(placeId, page, rnd)
    }

    private fun fakeAlts(placeId: String, page: Int, rnd: Random): List<Alternative> {
        // 每頁 3 筆
        return (1..3).map { i ->
            val idx = page * 3 + i
            val rating = 3.5 + rnd.nextDouble() * 1.5
            Alternative(
                placeId = "${placeId}_ALT_$idx",
                name = "替代景點 #$idx",
                address = "台南市中西區假路 ${10 + idx} 號",
                rating = String.format("%.1f", rating).toDouble(),
                userRatingsTotal = 50 + rnd.nextInt(500),
                lat = 22.99 + rnd.nextDouble() * 0.02,
                lng = 120.20 + rnd.nextDouble() * 0.02,
                openStatusText = listOf("營業中 · 至 20:30", "已打烊 · 明 09:00", null).random(rnd),
                photoUrl = null
            )
        }
    }
}
