package com.example.thelastone.ui.screens.comp.placedetaildialog.comp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng       // ✅ 別名匯入
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

@Composable
fun MapSection(lat: Double, lng: Double) {
    val point: GmsLatLng = remember(lat, lng) { GmsLatLng(lat, lng) }   // ✅ 指定型別
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(point, 15f)
    }
    val markerState = rememberMarkerState(position = point)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        GoogleMap(
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                compassEnabled = false,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
            ),
            properties = MapProperties(
                isMyLocationEnabled = false
            )
        ) {
            Marker(
                state = markerState,
                title = "目的地",
                snippet = "$lat, $lng"
            )
        }
    }
}