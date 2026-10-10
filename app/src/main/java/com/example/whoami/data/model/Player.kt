package com.example.whoami.data.model

/**
 * A single player inside a room.
 * Stored at /rooms/{roomCode}/players/{uid}
 *
 * No-arg constructor is required for Firebase Realtime Database deserialization.
 */
data class Player(
    val uid: String = "",
    val name: String = "",
    val joinedAt: Long = 0L,
    val hasSubmitted: Boolean = false,
    val hasGuessedCorrectly: Boolean = false
)
