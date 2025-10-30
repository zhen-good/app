package com.example.thelastone.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.ui.screens.comp.PlaceCard
import com.example.thelastone.ui.screens.comp.SectionHeader
import com.example.thelastone.ui.state.EmptyState
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun SpotsSection(
    modifier: Modifier = Modifier,
    title: String = "Popular Spots",
    isLoading: Boolean,
    error: String?,
    places: List<PlaceLite>,
    onOpenPlace: (String) -> Unit,
    onRetry: () -> Unit = {},
    itemsPerPage: Int = 3,
    autoScroll: Boolean = true,
    autoScrollMillis: Long = 4_000L,
    savedIds: Set<String> = emptySet(),
    onToggleSave: (PlaceLite) -> Unit = {},
    onRefresh: () -> Unit // 👈 新增：把 refresh 行為從外面傳進來
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            text = title,
            large = false,
            secondaryTone = true,
            bottomSpace = 12.dp,
            sticky = false,
            trailing = {
                IconButton(
                    onClick = onRefresh,           // ✅ 改呼叫參數
                    enabled = !isLoading           // ✅ 用現成的 isLoading 控制
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh spots"
                        )
                    }
                }
            }
        )

        when {
            isLoading -> {
                LoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    message = "景點載入中…"
                )
                return
            }
            error != null -> {
                ErrorState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    message = error,
                    onRetry = onRetry
                )
                return
            }
            places.isEmpty() -> {
                EmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    title = "目前找不到推薦景點",
                    description = "建議稍後再試或調整條件"
                )
                return
            }
        }

        val pageCount = remember(places, itemsPerPage) {
            maxOf(1, ceil(places.size / itemsPerPage.toFloat()).toInt())
        }
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
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1)
            ),
            pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                pagerState, Orientation.Horizontal
            ),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val start = page * itemsPerPage
            val end = minOf(start + itemsPerPage, places.size)
            val slice = places.subList(start, end)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                slice.forEach { p ->
                    PlaceCard(
                        place = p,
                        onClick = { onOpenPlace(p.placeId) },
                        isSaved = savedIds.contains(p.placeId),
                        onToggleSave = { onToggleSave(p) }
                    )
                }
                repeat(itemsPerPage - slice.size) { Spacer(Modifier.height(0.dp)) }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
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