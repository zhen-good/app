package com.example.thelastone.ui.screens.comp.placedetaildialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.ActionButtonsRow
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.ImgSection
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.MapSection
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.OpeningHoursSection
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.PlaceActionMode
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.RatingSection
import com.example.thelastone.vm.PlaceDetailViewModel

@Composable
fun PlaceDetailDialogHost(
    place: PlaceLite?,
    mode: PlaceActionMode,
    onDismiss: () -> Unit,
    onConfirmRight: () -> Unit,
) {
    val vm: PlaceDetailViewModel = hiltViewModel()
    val confirmRight by rememberUpdatedState(onConfirmRight)

    LaunchedEffect(place?.placeId) {
        if (place != null) vm.show(place) else vm.dismiss()
    }

    val uiState by vm.state.collectAsStateWithLifecycle()
    val lite = uiState.lite
    val details = uiState.details

    // place 為空就不顯示
    if (place == null || lite == null) return

    val merged = lite.copy(
        address          = details?.address           ?: lite.address,
        photoUrl         = details?.photoUrl          ?: lite.photoUrl,
        openingHours     = details?.openingHours      ?: lite.openingHours,
        openNow          = details?.openNow           ?: lite.openNow,
        openStatusText   = details?.openStatusText    ?: lite.openStatusText,
        rating           = details?.rating            ?: lite.rating,
        userRatingsTotal = details?.userRatingsTotal  ?: lite.userRatingsTotal,
    )

    PlaceDetailDialog(
        place = merged,
        mode = mode,
        onDismiss = onDismiss,
        onRemoveFromFavorite = { confirmRight() },
        onAddToFavorite = { confirmRight() },
        onAddToItinerary = { confirmRight() },
        loadingDetails = uiState.loadingDetails,
        error = uiState.error
    )
}

@Composable
private fun ColumnScope.Section(
    visible: Boolean,
    topSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    Spacer(Modifier.height(topSpacing))
    content()
}

@Composable
fun PlaceDetailDialog(
    place: PlaceLite?,  // null = 顯示 loading skeleton
    mode: PlaceActionMode = PlaceActionMode.ADD_TO_ITINERARY,
    onDismiss: () -> Unit,
    onAddToItinerary: () -> Unit = {},
    onRemoveFromFavorite: () -> Unit = {},
    onAddToFavorite: () -> Unit = {},
    loadingDetails: Boolean = false,   // ⬅️ 新增
    error: String? = null
) {
    // Loading skeleton
    if (place == null) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.9f)
                    .heightIn(max = 600.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 76.dp) // 給底部按鈕空間
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1) 圖片：有才顯示
                    place.photoUrl?.let { url -> ImgSection(url = url) }

                    Column(Modifier.padding(20.dp)) {
                        Text(place.name, style = MaterialTheme.typography.titleLarge)

                        if (loadingDetails) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // error 提醒（可選）
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // 3) 地址
                        Section(visible = !place.address.isNullOrBlank(), topSpacing = 4.dp) {
                            Text(place.address.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        }

                        // 4) 營業資訊
                        Section(
                            visible = place.openingHours.isNotEmpty() || place.openStatusText != null,
                            topSpacing = 8.dp
                        ) {
                            OpeningHoursSection(
                                hours = place.openingHours,
                                statusText = place.openStatusText
                            )
                        }

                        // 5) 評分
                        Section(visible = place.rating != null, topSpacing = 8.dp) {
                            RatingSection(
                                rating = place.rating ?: 0.0,
                                totalReviews = place.userRatingsTotal ?: 0
                            )
                        }

                        // 6) 地圖（總是顯示）
                        Section(visible = true, topSpacing = 12.dp) {
                            MapSection(lat = place.lat, lng = place.lng)
                        }
                    }
                }

                // 底部固定按鈕列
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ActionButtonsRow(
                        place = place,
                        rightButtonLabel = when (mode) {
                            PlaceActionMode.ADD_TO_ITINERARY -> "加入行程"
                            PlaceActionMode.ADD_TO_FAVORITE -> "加入最愛"
                            PlaceActionMode.REMOVE_FROM_FAVORITE -> "移除最愛"
                            PlaceActionMode.REPLACE_IN_ITINERARY -> "更換行程" // ← 新增
                        },
                        onRightButtonClick = {
                            when (mode) {
                                PlaceActionMode.ADD_TO_ITINERARY -> onAddToItinerary()
                                PlaceActionMode.ADD_TO_FAVORITE -> onAddToFavorite()
                                PlaceActionMode.REMOVE_FROM_FAVORITE -> onRemoveFromFavorite()
                                PlaceActionMode.REPLACE_IN_ITINERARY -> onAddToItinerary() // ← 沿用右鍵 callback
                            }
                        },
                        onLeftCancel = onDismiss
                    )
                }
            }
        }
    }
}