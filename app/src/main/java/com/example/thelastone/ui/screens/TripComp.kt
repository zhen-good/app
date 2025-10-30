package com.example.thelastone.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.thelastone.data.model.Activity
import com.example.thelastone.data.model.AgeBand
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.model.User
import com.example.thelastone.ui.screens.comp.Avatar
import com.example.thelastone.ui.state.EmptyState
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripInfoCard(
    trip: Trip,
    modifier: Modifier = Modifier,
    tonalSecondary: Boolean = true
) {
    val container = if (tonalSecondary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val onContainer = if (tonalSecondary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(            // ← 卡片（非 Elevated）
            containerColor = container,
            contentColor   = onContainer
        ),
        elevation = CardDefaults.cardElevation(      // ← 無陰影
            defaultElevation  = 0.dp,
            pressedElevation  = 0.dp,
            focusedElevation  = 0.dp,
            hoveredElevation  = 0.dp,
            draggedElevation  = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)   // ← 大節奏 12dp
        ) {
            Text(
                trip.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 次要資訊：用 onSurfaceVariant（一致的次要語氣）
            Text(
                "${trip.startDate} – ${trip.endDate}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (trip.activityStart != null && trip.activityEnd != null) {
                Text(
                    "活動時間：${trip.activityStart} ~ ${trip.activityEnd}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(2.dp)
            ) {
                trip.totalBudget?.let { CompactTag("NT$$it") }
                if (trip.avgAge != AgeBand.IGNORE) CompactTag(trip.avgAge.label())
                trip.styles.forEach { CompactTag(it) }
                trip.transportPreferences.forEach { CompactTag(it) }
            }
            Spacer(Modifier.height(2.dp))
            if (trip.members.isNotEmpty()) {
                MembersSection(members = trip.members)
            }
        }
    }
}

@Composable
fun CompactTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,     // 小字
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp) // 高度約 24–28dp
        )
    }
}

@Composable
private fun MembersSection(
    members: List<User>,
    maxShown: Int = 5
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { // 微節奏 8dp
        Text(
            "Members（${members.size}）",
            style = MaterialTheme.typography.titleSmall,        // ← 區塊小標
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            members.take(maxShown).forEach { user ->
                AvatarNameHint(user = user, size = 32.dp)
            }
            val more = members.size - maxShown
            if (more > 0) {
                AssistChip(
                    onClick = {},
                    label = { Text("+$more") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun AvatarNameHint(user: User, size: Dp) {
    var show by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .semantics { contentDescription = user.name } // a11y：等同 alt
    ) {
        // 頭貼：點一下顯示 1.5 秒
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    show = true
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        show = false
                    }
                }
        ) {
            Avatar(imageUrl = user.avatarUrl, size = size)
        }

        // 簡易 tooltip：浮在頭貼上方一點點
        if (show) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-8).dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = user.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}


private fun AgeBand.label(): String = when (this) {
    AgeBand.IGNORE -> "不列入"
    AgeBand.UNDER_17 -> "17以下"
    AgeBand.A18_25 -> "18–25"
    AgeBand.A26_35 -> "26–35"
    AgeBand.A36_45 -> "36–45"
    AgeBand.A46_55 -> "46–55"
    AgeBand.A56_PLUS -> "56以上"
}

@Composable
private fun ActivityRow(activity: Activity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,     // ← 柔和底色
            contentColor   = MaterialTheme.colorScheme.onSecondaryContainer    // ← 預設文字/圖示色
        ),
        elevation = CardDefaults.cardElevation(0.dp)   // ← 無陰影更柔和
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sub = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.80f)
            val time = listOfNotNull(activity.startTime, activity.endTime)
                .takeIf { it.isNotEmpty() }?.joinToString(" ~ ") ?: "未設定時間"
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(activity.place.name, style = MaterialTheme.typography.titleMedium)
                Text(time, style = MaterialTheme.typography.bodyMedium, color = sub)
            }

            if (!activity.place.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = activity.place.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun LazyListScope.dayTabsAndActivities(
    trip: Trip,
    selected: Int,
    onSelect: (Int) -> Unit,
    onActivityClick: (dayIndex: Int, activityIndex: Int, activity: Activity) -> Unit
) {
    val monthDayFormatter = DateTimeFormatter.ofPattern("MM-dd")
    val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    stickyHeader {
        ScrollableTabRow(
            selectedTabIndex = selected,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            edgePadding = 0.dp,
            indicator = { pos ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(pos[selected]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            trip.days.forEachIndexed { i, d ->
                // 將 d.date 轉成 LocalDate 後再格式化（兼容 String / LocalDate / LocalDateTime）
                val monthDayText = when (val date = d.date) {
                    is java.time.LocalDate -> date.format(monthDayFormatter)
                    is java.time.LocalDateTime -> date.toLocalDate().format(monthDayFormatter)
                    is String -> java.time.LocalDate.parse(date, isoFormatter).format(monthDayFormatter)
                    else -> d.date.toString() // 萬一是奇怪型別，至少不會崩
                }

                Tab(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    text = {
                        Text(
                            text = "第 ${i + 1} 天\n$monthDayText",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val day = trip.days.getOrNull(selected)
    if (day == null || day.activities.isEmpty()) {
        item {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                title = "沒有行程",
                description = "尚未產生任何每日活動"
            )
        }
    } else {
        items(day.activities.size, key = { idx -> day.activities[idx].id }) { idx ->
            val act = day.activities[idx]
            ActivityRow(activity = act) { onActivityClick(selected, idx, act) }
        }
    }
}