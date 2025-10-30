package com.example.thelastone.utils

import com.example.thelastone.data.model.PlaceLite
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

// 之後 PickPlace/AddActivity/EditActivity 都共用(配合 PlaceLite)
private val json = Json { ignoreUnknownKeys = true }

fun encodePlaceArg(placeLite: PlaceLite): String =
    URLEncoder.encode(json.encodeToString(PlaceLite.serializer(), placeLite), "UTF-8")

fun decodePlaceArg(encoded: String): PlaceLite =
    json.decodeFromString(
        PlaceLite.serializer(),
        URLDecoder.decode(encoded, "UTF-8")
    )
