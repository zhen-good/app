package com.example.thelastone.ui.screens.comp.placedetaildialog.comp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.thelastone.utils.formatTimeRange24h
import com.example.thelastone.utils.getOpeningStatusInfo
import java.time.LocalDate
import java.time.LocalTime

/* =============== OpeningHoursSection =============== */

@Composable
fun OpeningHoursSection(
    hours: List<String>,
    statusText: String?,                      // 已計算好的文案（優先）
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val headerText = statusText ?: run {
        val now = remember { LocalTime.now() }
        getOpeningStatusInfo(hours, now, colorScheme).text
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        ExpandableHeader(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(HeaderIconSize)
                )
            },
            title = headerText,
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )

        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SectionHPadding, end = SectionHPadding, bottom = SectionVPadding)
            ) {
                val today = LocalDate.now().dayOfWeek.name
                hours.forEach { line ->
                    val parts = line.split(":", limit = 2).map { it.trim() }
                    val dayEn = parts.getOrNull(0) ?: return@forEach
                    val time = parts.getOrNull(1) ?: ""
                    val isToday = dayEn.uppercase() == today
                    val color = if (isToday) colorScheme.onSurface else colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = InnerRowHPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(dayEn.toChineseDay(), color = color, style = MaterialTheme.typography.bodyLarge)
                        Text(formatTimeRange24h(time), color = color, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun String.toChineseDay(): String = when (uppercase()) {
    "MONDAY" -> "星期一"
    "TUESDAY" -> "星期二"
    "WEDNESDAY" -> "星期三"
    "THURSDAY" -> "星期四"
    "FRIDAY" -> "星期五"
    "SATURDAY" -> "星期六"
    "SUNDAY" -> "星期日"
    else -> this
}
