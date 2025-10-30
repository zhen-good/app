package com.example.thelastone.ui.screens.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// PATCH: SectionHeader.kt
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    secondaryTone: Boolean = false,
    bold: Boolean = false,
    bottomSpace: Dp = 12.dp,
    sticky: Boolean = false,
    trailing: @Composable (() -> Unit)? = null // 👈 新增
) {
    val typography = MaterialTheme.typography
    val color = if (secondaryTone) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
    val baseStyle = if (large) typography.headlineSmall else typography.titleMedium

    val bgModifier = if (sticky) {
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
    } else modifier

    Row( // 👈 改成 Row，好讓 trailing 右貼齊
        modifier = bgModifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = if (bold) baseStyle.copy(fontWeight = FontWeight.SemiBold) else baseStyle,
            color = color,
            modifier = Modifier.padding(end = 12.dp)
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
    if (bottomSpace > 0.dp) Spacer(Modifier.height(bottomSpace))
}
