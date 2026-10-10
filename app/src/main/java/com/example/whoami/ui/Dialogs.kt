package com.example.whoami.ui

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Activity.confirmLeave(isHost: Boolean, onLeave: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Leave this room?")
        .setMessage(
            if (isHost) "You’re the host, so leaving closes the room for everyone."
            else "You’ll drop out of this game."
        )
        .setPositiveButton("Leave") { _, _ -> onLeave() }
        .setNegativeButton("Stay", null)
        .show()
}
