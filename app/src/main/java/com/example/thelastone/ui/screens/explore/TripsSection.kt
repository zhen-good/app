package com.example.thelastone.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.thelastone.data.model.Trip
import com.example.thelastone.ui.screens.comp.SectionHeader
import com.example.thelastone.ui.screens.comp.TripCard
import kotlinx.coroutines.delay
import kotlin.collections.forEach
import kotlin.math.ceil

@Composable
fun TripsSection(
    modifier: Modifier = Modifier,
    title: String = "Popular Trips",
    trips: List<Trip>,
    onTripClick: (String) -> Unit,
    itemsPerPage: Int = 3,
    autoScroll: Boolean = true,
    autoScrollMillis: Long = 4_000L,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            text = title,
            large = false,
            secondaryTone = true,
            bottomSpace = 12.dp,
            sticky = false
        )

        if (trips.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("目前沒有推薦行程", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }
        val pageCount = remember(trips, itemsPerPage) { maxOf(1, ceil(trips.size / itemsPerPage.toFloat()).toInt()) }
        val pagerState = rememberPagerState(pageCount = { pageCount })

        LaunchedEffect(pageCount, autoScroll, autoScrollMillis) {
            if (!autoScroll || pageCount <= 1) return@LaunchedEffect
            while (true) {
                delay(autoScrollMillis)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % pageCount
                    pagerState.animateScrollToPage(next)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1)),
            pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val start = page * itemsPerPage
            val end = minOf(start + itemsPerPage, trips.size)
            val slice = trips.subList(start, end)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                slice.forEach { trip ->
                    TripCard(trip = trip, onClick = { onTripClick(trip.id) }, imageUrl = null, modifier = Modifier.fillMaxWidth())
                }
                repeat(itemsPerPage - slice.size) { Spacer(Modifier.height(0.dp)) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pagerState.pageCount) { i ->
                val selected = pagerState.currentPage == i
                val size = if (selected) 8.dp else 6.dp
                val alpha = if (selected) 1f else 0.45f
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(size)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
        }
    }
}