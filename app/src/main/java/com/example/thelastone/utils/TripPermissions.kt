package com.example.thelastone.utils

import com.example.thelastone.data.model.Trip

// TripPermissions.kt
data class TripPerms(
    val isOwner: Boolean,
    val isMember: Boolean
) {
    val canChat get() = isOwner || isMember
    val canEditTrip get() = isOwner
    val readOnly get() = !canChat
}

fun Trip.computePerms(currentUid: String): TripPerms {
    val memberIds: List<String> = this.members.map { it.id }
    return TripPerms(
        isOwner = (this.createdBy == currentUid),
        isMember = currentUid in memberIds
    )
}