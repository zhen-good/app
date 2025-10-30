package com.example.thelastone.utils

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// 放在 ExploreScreen.kt 同檔（或移到 utils.kt）
@SuppressLint("MissingPermission")
private suspend fun getLatLngWithRetries(
    ctx: Context,
    tries: Int = 5,
    intervalMs: Long = 600
): LatLng? = withContext(Dispatchers.Main) {
    val client = LocationServices.getFusedLocationProviderClient(ctx)
    repeat(tries) { i ->
        // 1) 先試 lastLocation（超快）
        val last = withContext(Dispatchers.IO) { client.lastLocation.awaitNullable() }
        if (last != null) return@withContext LatLng(last.latitude, last.longitude)

        // 2) 再試 request single fix（可能要等）
        val cur = withTimeoutOrNull(1500L) {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).awaitNullable()
        }
        if (cur != null) return@withContext LatLng(cur.latitude, cur.longitude)

        // 3) 下一輪
        delay(intervalMs)
    }
    return@withContext null
}

// 小工具：Task<T> 轉 suspend（nullable 版）
private suspend fun <T> Task<T>.awaitNullable(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) {} }
    addOnFailureListener { cont.resume(null) {} }
    addOnCanceledListener { cont.cancel() }
}

data class LatLng(val lat: Double, val lng: Double)
