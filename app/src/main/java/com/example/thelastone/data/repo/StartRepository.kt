package com.example.thelastone.data.repo

import com.example.thelastone.data.model.*
import com.example.thelastone.data.model.Place

interface StartRepository {
    suspend fun getStartInfo(place: Place): StartInfo
    suspend fun getAlternatives(placeId: String, page: Int): List<Alternative>
}
