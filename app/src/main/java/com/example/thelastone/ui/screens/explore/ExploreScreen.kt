package com.example.thelastone.ui.screens.explore

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.thelastone.data.model.PlaceLite
import com.example.thelastone.ui.screens.comp.placedetaildialog.PlaceDetailDialog
import com.example.thelastone.ui.screens.comp.placedetaildialog.comp.PlaceActionMode
import com.example.thelastone.ui.state.ErrorState
import com.example.thelastone.ui.state.LoadingState
import com.example.thelastone.vm.ExploreViewModel
import com.example.thelastone.vm.SavedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ⬇️ 新增：定位與協程工具
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = hiltViewModel(),
    savedVm: SavedViewModel = hiltViewModel(),
    padding: PaddingValues = PaddingValues(0.dp),
    openTrip: (String) -> Unit = {},
    openPlace: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // 只用一份狀態（不要再另外取一個 vm/state）
    val ui by viewModel.state.collectAsState()
    val savedUi by savedVm.state.collectAsState()
    var preview by remember { mutableStateOf<PlaceLite?>(null) }

    // ---- 小工具：判斷目前是否已授權 ----
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    // ---- 依「目前權限狀態」載入 Spots 的統一入口 ----
    fun reloadSpots() {
        if (ui.spotsLoading) return // 避免重複打
        if (hasLocationPermission()) {
            scope.launch {
                val p = getLatLngWithRetries(ctx, tries = 5, intervalMs = 600)
                if (p != null) viewModel.loadSpotsAroundMe(lat = p.lat, lng = p.lng)
                else viewModel.loadSpotsTaiwan()
            }
        } else {
            viewModel.loadSpotsTaiwan()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            scope.launch {
                val p = getLatLngWithRetries(ctx, tries = 5, intervalMs = 600)
                if (p != null) {
                    viewModel.loadSpotsAroundMe(lat = p.lat, lng = p.lng)
                } else {
                    // 不立即降級，安排一次晚點重試
                    delay(1500)
                    val p2 = getLatLngWithRetries(ctx, tries = 3, intervalMs = 800)
                    if (p2 != null) viewModel.loadSpotsAroundMe(lat = p2.lat, lng = p2.lng)
                    else viewModel.loadSpotsTaiwan() // 最後才退回
                }
            }
        } else {
            viewModel.loadSpotsTaiwan()
        }
    }

    val askedPermissionOnce = rememberSaveable { mutableStateOf(false) }

    // ⬇️ 初次進頁：也改用「重試版取位」
    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            val p = getLatLngWithRetries(ctx, tries = 5, intervalMs = 600)
            if (p != null) viewModel.loadSpotsAroundMe(lat = p.lat, lng = p.lng)
            else viewModel.loadSpotsTaiwan()
        } else if (!askedPermissionOnce.value) {
            askedPermissionOnce.value = true
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            // 回到此頁且使用者已拒絕過，就直接給台灣熱門（避免再次彈窗）
            viewModel.loadSpotsTaiwan()
        }
    }

    // ---- 監聽 ON_RESUME：偵測「無權限→有權限」的變化 ----
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastPermissionGranted = remember { mutableStateOf(hasLocationPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = hasLocationPermission()
                val wasGranted = lastPermissionGranted.value
                lastPermissionGranted.value = nowGranted

                if (nowGranted && !wasGranted) {
                    // 權限剛變允許 → 優先嘗試附近（含重試），取不到再排背景嘗試
                    scope.launch {
                        val p = getLatLngWithRetries(ctx, tries = 5, intervalMs = 600)
                        if (p != null) {
                            viewModel.loadSpotsAroundMe(lat = p.lat, lng = p.lng)
                        } else {
                            // 背景再試一次
                            launch {
                                delay(2000)
                                val p2 = getLatLngWithRetries(ctx, tries = 3, intervalMs = 800)
                                if (p2 != null) viewModel.loadSpotsAroundMe(lat = p2.lat, lng = p2.lng)
                                // 還是沒有 → 保持現狀（可能仍是 Taiwan），交給使用者下拉刷新再試
                            }
                        }
                    }
                }
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)
    ) {
        when {
            ui.isLoading -> LoadingState(Modifier.fillMaxSize(), "載入熱門行程中…")
            ui.error != null -> ErrorState(
                Modifier.fillMaxSize(),
                ui.error!!,
                onRetry = { viewModel.retry() } // 這是 Trips 的重試
            )
            else -> {
                // Trips 區(popular trip)
                TripsSection(
                    title = "Popular Trips",
                    trips = ui.popularTrips,
                    onTripClick = openTrip,
                    itemsPerPage = 3,
                    autoScroll = true,
                    autoScrollMillis = 4000L
                )

                if (!ui.spotsInitialized) {
                    LoadingState(Modifier.fillMaxWidth(), "正在準備熱門景點…")
                } else {
                    SpotsSection(
                        title = "Popular Spots",
                        isLoading = ui.spotsLoading,
                        error = ui.spotsError,
                        places = ui.spots,
                        onOpenPlace = { id -> preview = ui.spots.firstOrNull { it.placeId == id } },
                        savedIds = savedUi.savedIds,
                        onToggleSave = { place -> savedVm.toggle(place) },
                        onRetry = { reloadSpots() },      // 失敗時重試
                        onRefresh = { reloadSpots() }     // 👈 右上角 Refresh icon
                    )
                }

                // Dialog
                if (preview != null) {
                    val isSaved = savedUi.savedIds.contains(preview!!.placeId)
                    val mode = if (isSaved) PlaceActionMode.REMOVE_FROM_FAVORITE
                    else PlaceActionMode.ADD_TO_FAVORITE

                    PlaceDetailDialog(
                        place = preview,
                        mode = mode,
                        onDismiss = { preview = null },
                        onAddToFavorite = {
                            preview?.let { savedVm.toggle(it) }
                            preview = null
                        },
                        onRemoveFromFavorite = {
                            preview?.let { savedVm.toggle(it) }
                            preview = null
                        }
                    )
                }
            }
        }
    }
}

/* =================== 以下是「兩階段取位 + 重試」工具 =================== */

data class LatLng(val lat: Double, val lng: Double)

@SuppressLint("MissingPermission")
private suspend fun getLatLngWithRetries(
    ctx: Context,
    tries: Int = 5,
    intervalMs: Long = 600
): LatLng? = withContext(Dispatchers.Main) {
    val client = LocationServices.getFusedLocationProviderClient(ctx)
    repeat(tries) {
        // 1) 先試 lastLocation（快）
        val last = withContext(Dispatchers.IO) { client.lastLocation.awaitNullable() }
        if (last != null) return@withContext LatLng(last.latitude, last.longitude)

        // 2) 再試 single fix（可能要等）
        val cur = withTimeoutOrNull(1500L) {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, /* cancellationToken = */ null
            ).awaitNullable()
        }
        if (cur != null) return@withContext LatLng(cur.latitude, cur.longitude)

        // 3) 下一輪
        delay(intervalMs)
    }
    return@withContext null
}

// Task<T> → suspend（nullable 版）
private suspend fun <T> Task<T>.awaitNullable(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) {} }
    addOnFailureListener { cont.resume(null) {} }
    addOnCanceledListener { cont.cancel() }
}