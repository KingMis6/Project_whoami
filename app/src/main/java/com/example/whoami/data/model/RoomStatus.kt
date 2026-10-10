package com.example.whoami.data.model

/**
 * Lifecycle of a game room. Stored as a plain string at /rooms/{roomCode}/status
 * (Realtime DB doesn't store enums natively, so we convert to/from .name).
 */
enum class RoomStatus {
    LOBBY,       // waiting for players to join (2-6)
    SUBMITTING,  // each player is privately typing in a character
    ASSIGNED,    // host has shuffled + assigned characters, reveal screen shown
    PLAYING,     // turn-based Q&A / guessing in progress
    FINISHED     // everyone has guessed (or game was ended)
}
