package com.example.thelastone.ui.screens.comp.placedetaildialog.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* =============== 共用樣式 token =============== */

val SectionHPadding = 16.dp
val SectionVPadding = 12.dp
val HeaderIconSize = 20.dp
val HeaderChevronSize = 20.dp
val RowGap = 8.dp
val InnerRowHPadding = 12.dp
val BarHeight = 6.dp
val BarRadius = 3.dp

/* =============== 共用 Header =============== */

@Composable
fun ExpandableHeader(
    leadingIcon: @Composable () -> Unit,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = SectionVPadding)
    ) {
        leadingIcon()
        Spacer(Modifier.width(RowGap))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        // 統一用旋轉的 Chevron 表示展開狀態
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(HeaderChevronSize)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f }
        )
    }
}