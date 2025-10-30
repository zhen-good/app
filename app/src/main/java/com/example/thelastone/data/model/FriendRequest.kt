package com.example.thelastone.data.model

data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val status: String // "pending", "accepted", "rejected"
) {
    val isPending: Boolean get() = status == "pending"
}