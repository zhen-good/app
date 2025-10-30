package com.example.thelastone.utils

// LocationUtils.kt
import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

data class SimpleLatLng(val lat: Double, val lng: Double)

@SuppressLint("MissingPermission")
suspend fun getCurrentLatLngOrNull(context: Context): SimpleLatLng? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    // 先嘗試 lastLocation（速度快）；拿不到再嘗試一次性定位
    val last = client.lastLocation.await()
    if (last != null) return SimpleLatLng(last.latitude, last.longitude)

    val priority = com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
    val loc = client.getCurrentLocation(priority, /* cancellationToken= */ null).await()
    return loc?.let { SimpleLatLng(it.latitude, it.longitude) }
}
