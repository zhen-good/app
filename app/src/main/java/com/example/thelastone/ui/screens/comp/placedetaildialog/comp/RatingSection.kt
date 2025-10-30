package com.example.thelastone.ui.screens.comp.placedetaildialog.comp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/* =============== RatingSection =============== */

@Composable
fun RatingSection(
    rating: Double,
    totalReviews: Int,
    modifier: Modifier = Modifier,
    ratingDistribution: Map<Int, Float> = mapOf(
        5 to 0.4f, 4 to 0.3f, 3 to 0.15f, 2 to 0.1f, 1 to 0.05f
    )
) {
    var expanded by remember { mutableStateOf(false) }

    val header = "${String.format("%.1f", rating)} · ${totalReviews}則評價"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        ExpandableHeader(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(HeaderIconSize)
                )
            },
            title = header,
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )

        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SectionHPadding, end = SectionHPadding, bottom = SectionVPadding)
            ) {
                (5 downTo 1).forEach { stars ->
                    val percent = ratingDistribution[stars] ?: 0f
                    RatingBar(stars = stars, percent = percent)
                }
            }
        }
    }
}

@Composable
private fun RatingBar(stars: Int, percent: Float) {
    val colorScheme = MaterialTheme.colorScheme
    val starColor = Color(0xFFFFC107)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = InnerRowHPadding)
    ) {
        // 左側：幾顆星
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(56.dp)) {
            Text(text = stars.toString(), style = MaterialTheme.typography.bodyLarge, color = colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(16.dp)
            )
        }
        // 右側：Progress Bar
        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier
                .weight(1f)
                .height(BarHeight)
                .clip(RoundedCornerShape(BarRadius)),
            color = starColor,
            trackColor = colorScheme.surfaceVariant
        )
    }
}
