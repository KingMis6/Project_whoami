package com.example.whoami.data.model

/**
 * A character someone typed in. imageUrl is best-effort — filled in
 * automatically by ImageLookupService when possible, left null if no
 * picture could be found (the UI just shows a placeholder in that case).
 *
 * No-arg constructor + defaults required for Firebase deserialization.
 */
data class Character(
    val name: String = "",
    val imageUrl: String? = null
)
