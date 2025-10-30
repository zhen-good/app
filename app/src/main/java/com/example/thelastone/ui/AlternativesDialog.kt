package com.example.thelastone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.thelastone.data.model.Alternative
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.ui.screens.comp.placedetaildialog.PlaceDetailDialogHost
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.PlaceActionMode

private fun Alternative.toPlaceLite(): PlaceLite = PlaceLite(
    placeId = placeId,
    name = name,
    lat = lat,
    lng = lng,
    address = address,
    rating = rating,
    userRatingsTotal = userRatingsTotal,
    photoUrl = photoUrl,
    openingHours = emptyList(),
    openNow = null,
    openStatusText = openStatusText
)

@Composable
fun AlternativesDialog(
    alts: List<Alternative>,
    onDismiss: () -> Unit,
    onPick: (Alternative) -> Unit,
    onSeeMore: () -> Unit
) {
    var preview by remember { mutableStateOf<Alternative?>(null) }
    val display = remember(alts) { alts.take(3) } // 左上、右上、左下；右下保留給查看更多

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更換行程") },
        text = {
            // 用 BoxWithConstraints 取得可用寬度 → 推算正方形尺寸與容器高度
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val hSpacing = 12.dp
                val vSpacing = 12.dp

                // 顯式使用接收者：this@BoxWithConstraints 或 this
                val side = (this.maxWidth - hSpacing) / 2
                val gridHeight = side * 2 + vSpacing

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(vSpacing),
                    horizontalArrangement = Arrangement.spacedBy(hSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight)
                ) {
                    items(display) { a ->
                        AltSquareCard(alt = a, onClick = { preview = a })
                    }
                    repeat(maxOf(0, 3 - display.size)) { item { Spacer(Modifier.fillMaxWidth().aspectRatio(1f)) } }
                    item { SeeMoreSquareCard(onSeeMore = onSeeMore) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // 點卡片後 → 開細節；右下改「更換行程」
    if (preview != null) {
        PlaceDetailDialogHost(
            place = preview!!.toPlaceLite(),
            mode = PlaceActionMode.REPLACE_IN_ITINERARY,
            onDismiss = { preview = null },
            onConfirmRight = {
                onPick(preview!!)
                preview = null       // 關閉內層
                onDismiss()          // 關閉 AlternativesDialog
            }
        )
    }
}

/** 單一替代方案：正方形卡片（上圖下名） */
@Composable
private fun AltSquareCard(
    alt: Alternative,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // 正方形
    ) {
        Column(Modifier.fillMaxSize()) {
            // 圖片佔滿可用高度（保留底部名稱區塊）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (alt.photoUrl != null) {
                    SubcomposeAsyncImage(
                        model = alt.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    ) {
                        val state = painter.state
                        if (state is coil.compose.AsyncImagePainter.State.Loading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    // 無圖：柔和占位
                    Box(
                        Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No Photo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 名稱（單行省略）
            Text(
                text = alt.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}

/** 右下角「查看更多」：正方形卡片 */
@Composable
private fun SeeMoreSquareCard(
    onSeeMore: () -> Unit
) {
    ElevatedCard(
        onClick = onSeeMore,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "查看更多",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}