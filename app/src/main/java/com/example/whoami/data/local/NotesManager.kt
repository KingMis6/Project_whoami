package com.example.whoami.data.local

import android.content.Context

/**
 * Each player's scratchpad is 100% local to their own device — it's their
 * private deduction notes, so it's never written to Firebase. Keyed by
 * room code so notes don't bleed between games.
 */
class NotesManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("who_am_i_notes", Context.MODE_PRIVATE)

    fun getNotes(roomCode: String): String {
        return prefs.getString(keyFor(roomCode), "") ?: ""
    }

    fun saveNotes(roomCode: String, text: String) {
        prefs.edit().putString(keyFor(roomCode), text).apply()
    }

    fun clearNotes(roomCode: String) {
        prefs.edit().remove(keyFor(roomCode)).apply()
    }

    private fun keyFor(roomCode: String) = "notes_$roomCode"
}
