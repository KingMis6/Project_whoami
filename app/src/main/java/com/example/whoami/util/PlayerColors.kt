package com.example.whoami.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.whoami.R

/** One soft pastel per player, by join order. Wraps around after 6. */
object PlayerColors {
    private val ids = intArrayOf(
        R.color.player_pink,
        R.color.player_sky,
        R.color.player_mint,
        R.color.player_peach,
        R.color.player_butter,
        R.color.player_lilac
    )

    fun color(context: Context, index: Int): Int =
        ContextCompat.getColor(context, ids[index.mod(ids.size)])
}
