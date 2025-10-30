package com.example.thelastone.data.repo.impl.fake

import com.example.thelastone.data.model.Activity
import com.example.thelastone.data.model.Trip
import com.example.thelastone.data.model.TripForm
import com.example.thelastone.data.model.TripVisibility
import com.example.thelastone.data.repo.TripRepository
import com.example.thelastone.data.repo.TripStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * TripRepository 的一個假實作 (Fake Implementation)，主要用於 UI 預覽和單元測試。
 * 它在記憶體中模擬資料庫的行為，不會進行任何真實的網路或資料庫操作。
 */
class FakeTripRepository @Inject constructor() : TripRepository {

    private var formForPreview: TripForm? = null
    private val fakeTrips = mutableListOf<Trip>()

    override suspend fun createTrip(form: TripForm): Trip {
        // 為了測試，回傳一個固定的假 Trip
        return Trip(
            id = "fake_trip_id_${System.currentTimeMillis()}",
            createdBy = "fake_user",
            name = form.name,
            locations = form.locations,
            totalBudget = form.totalBudget,
            startDate = form.startDate,
            endDate = form.endDate,
            activityStart = form.activityStart,
            activityEnd = form.activityEnd,
            avgAge = form.avgAge,
            transportPreferences = form.transportPreferences,
            useGmapsRating = form.useGmapsRating,
            styles = form.styles,
            visibility = form.visibility,
            members = emptyList(),
            days = emptyList()
        )
    }

    override suspend fun saveTrip(trip: Trip): Trip {
        // 模擬儲存行為：將 trip 加入到記憶體的列表中
        fakeTrips.removeAll { it.id == trip.id } // 如果已存在則先移除舊的
        fakeTrips.add(trip)
        return trip
    }

    // 🔽 [ [ [ 以下是為了遵守完整合約而新增的假實作 ] ] ] 🔽

    override suspend fun getMyTrips(): List<Trip> {
        return fakeTrips
    }

    override fun observeMyTrips(): Flow<List<Trip>> {
        return flowOf(fakeTrips)
    }

    override suspend fun getPublicTrips(): List<Trip> {
        return fakeTrips.filter { it.visibility == TripVisibility.PUBLIC }
    }

    override fun observePublicTrips(): Flow<List<Trip>> {
        return flowOf(fakeTrips.filter { it.visibility == TripVisibility.PUBLIC })
    }

    override suspend fun getTripDetail(tripId: String): Trip {
        return fakeTrips.first { it.id == tripId }
    }

    override fun observeTripDetail(tripId: String): Flow<Trip> {
        return flowOf(fakeTrips.first { it.id == tripId })
    }

    override suspend fun addActivity(tripId: String, dayIndex: Int, activity: Activity) {
        // 假實作：在測試中可以忽略此操作
    }

    override suspend fun updateActivity(tripId: String, dayIndex: Int, activityIndex: Int, updated: Activity) {
        // 假實作：在測試中可以忽略此操作
    }

    override suspend fun removeActivity(tripId: String, dayIndex: Int, activityIndex: Int) {
        // 假實作：在測試中可以忽略此操作
    }

    override suspend fun deleteTrip(tripId: String) {
        fakeTrips.removeAll { it.id == tripId }
    }



    override suspend fun addMembers(tripId: String, userIds: List<String>) {
        // 假實作：在測試中可以忽略此操作
    }

    override suspend fun getTripStatsFor(userId: String): TripStats {
        // 假實作：回傳固定的假統計資料
        return TripStats(created = 0, participating = 0)
    }

    // --- 我們為 AI 行程生成新增的功能 ---
    override fun setTripFormForPreview(form: TripForm) {
        this.formForPreview = form
    }

    override fun getTripFormForPreview(): TripForm? {
        val form = this.formForPreview
        this.formForPreview = null
        return form
    }
}

