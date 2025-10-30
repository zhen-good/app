package com.example.thelastone.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

fun openNavigation(context: Context, lat: Double, lng: Double, name: String) {
    // google.navigation:q=lat,lng(label)
    val uri = Uri.parse("google.navigation:q=$lat,$lng(${Uri.encode(name)})&mode=d")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 退回一般 Maps 網頁路徑
        val web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        context.startActivity(Intent(Intent.ACTION_VIEW, web))
    }
}
