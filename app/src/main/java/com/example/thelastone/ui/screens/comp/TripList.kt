package com.example.thelastone.ui.screens.comp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.model.coverPhotoUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun parseDateOrNull(s: String?): LocalDate? =
    try { if (s.isNullOrBlank()) null else LocalDate.parse(s, DATE_FMT) } catch (_: Exception) { null }

private enum class TripBucket(val title: String) {
    Upcoming("Upcoming"),
    Ongoing("Ongoing"),
    Finished("Finished")
}

private fun bucketOf(trip: Trip, today: LocalDate): TripBucket {
    val s = parseDateOrNull(trip.startDate)
    val e = parseDateOrNull(trip.endDate)
    return when {
        e != null && e.isBefore(today)     -> TripBucket.Finished
        s != null && s.isAfter(today)      -> TripBucket.Upcoming
        else                               -> TripBucket.Ongoing
    }
}

// 起始日遞增；解析失敗的排在最後
private fun comparatorByStart(): Comparator<Trip> = compareBy(
    { parseDateOrNull(it.startDate) ?: LocalDate.MAX },
    { it.name }
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripList(trips: List<Trip>, openTrip: (String) -> Unit) {
    val today = remember { LocalDate.now() }

    // 分桶
    val (upcoming, ongoing, finished) = remember(trips) {
        val u = mutableListOf<Trip>()
        val o = mutableListOf<Trip>()
        val f = mutableListOf<Trip>()
        trips.forEach { t ->
            when (bucketOf(t, today)) {
                TripBucket.Upcoming -> u += t
                TripBucket.Ongoing  -> o += t
                TripBucket.Finished -> f += t
            }
        }
        u.sortWith(comparatorByStart())
        o.sortWith(comparatorByStart())
        f.sortWith(comparatorByStart())
        Triple(u, o, f)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Upcoming
        if (upcoming.isNotEmpty()) {
            stickyHeader {
                SectionHeader(
                    text = TripBucket.Upcoming.title,
                    secondaryTone = true,   // ← 建議跟 Explore 一致
                    bottomSpace = 0.dp,     // ← 這裡設 0，避免和 spacedBy(12.dp) 疊加
                    sticky = true           // ← 釘頭補 surface 背景，視覺穩定
                )
            }
            items(items = upcoming, key = { it.id }) { trip ->
                val coverUrl = remember(trip) { trip.coverPhotoUrl() }
                TripCard(trip = trip, imageUrl = coverUrl, onClick = { openTrip(trip.id) })
            }
        }

        // Ongoing
        if (ongoing.isNotEmpty()) {
            stickyHeader {
                SectionHeader(
                    text = TripBucket.Ongoing.title,
                    secondaryTone = true,   // ← 建議跟 Explore 一致
                    bottomSpace = 0.dp,     // ← 這裡設 0，避免和 spacedBy(12.dp) 疊加
                    sticky = true           // ← 釘頭補 surface 背景，視覺穩定
                )
            }
            items(items = ongoing, key = { it.id }) { trip ->
                val coverUrl = remember(trip) { trip.coverPhotoUrl() }
                TripCard(trip = trip, imageUrl = coverUrl, onClick = { openTrip(trip.id) })
            }
        }

        // Finished
        if (finished.isNotEmpty()) {
            stickyHeader {
                SectionHeader(
                    text = TripBucket.Finished.title,
                    secondaryTone = true,   // ← 建議跟 Explore 一致
                    bottomSpace = 0.dp,     // ← 這裡設 0，避免和 spacedBy(12.dp) 疊加
                    sticky = true           // ← 釘頭補 surface 背景，視覺穩定
                )
            }
            items(items = finished, key = { it.id }) { trip ->
                val coverUrl = remember(trip) { trip.coverPhotoUrl() }
                TripCard(trip = trip, imageUrl = coverUrl, onClick = { openTrip(trip.id) })
            }
        }
    }
}