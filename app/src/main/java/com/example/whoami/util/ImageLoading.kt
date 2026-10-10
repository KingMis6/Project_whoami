package com.example.whoami.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.whoami.R

/**
 * Loads a character's picture, or a soft silhouette placeholder if there isn't
 * one (or it fails to load). Nobody ever sees a broken-image icon.
 */
fun ImageView.loadCharacterImage(imageUrl: String?) {
    Glide.with(this)
        .load(imageUrl)
        .placeholder(R.drawable.ic_person_placeholder)
        .error(R.drawable.ic_person_placeholder)
        .fallback(R.drawable.ic_person_placeholder)
        .centerCrop()
        .into(this)
}

/** Cancels any in-flight load and empties the view (used for the hidden "?" card). */
fun ImageView.clearCharacterImage() {
    Glide.with(this).clear(this)
    setImageDrawable(null)
}
