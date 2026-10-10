package com.example.whoami.ui.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whoami.R
import com.example.whoami.databinding.ItemCharacterCardBinding
import com.example.whoami.util.PlayerColors
import com.example.whoami.util.clearCharacterImage
import com.example.whoami.util.loadCharacterImage

class CharacterCardAdapter : ListAdapter<CharacterCardAdapter.CardUi, CharacterCardAdapter.VH>(DIFF) {

    /**
     * One card per player. characterName == null means "hidden from you":
     * that's the local player's own unsolved card (they see a "?" instead).
     */
    data class CardUi(
        val uid: String,
        val ownerName: String,
        val colorIndex: Int,
        val characterName: String?,
        val imageUrl: String?,
        val isMe: Boolean,
        val isSolved: Boolean,
        val isCurrentTurn: Boolean
    )

    class VH(val b: ItemCharacterCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCharacterCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b
        val ctx = b.root.context
        val density = ctx.resources.displayMetrics.density

        b.card.setCardBackgroundColor(PlayerColors.color(ctx, item.colorIndex))
        b.card.strokeColor = ContextCompat.getColor(ctx, R.color.lavender_ink)
        b.card.strokeWidth = if (item.isCurrentTurn) (3 * density).toInt() else 0

        b.tvOwner.text = if (item.isMe) "You" else item.ownerName
        b.tvMystery.text = "?"

        if (item.characterName == null) {
            b.ivCharacter.clearCharacterImage()
            b.tvMystery.isVisible = true
            b.tvCharacterName.text = "Who am I?"
        } else {
            b.tvMystery.isVisible = false
            b.ivCharacter.loadCharacterImage(item.imageUrl)
            b.tvCharacterName.text = item.characterName
        }

        val status = when {
            item.isSolved -> "Solved ✓"
            item.isCurrentTurn -> "Guessing now…"
            else -> ""
        }
        b.tvStatus.text = status
        b.tvStatus.visibility = if (status.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CardUi>() {
            override fun areItemsTheSame(oldItem: CardUi, newItem: CardUi) = oldItem.uid == newItem.uid
            override fun areContentsTheSame(oldItem: CardUi, newItem: CardUi) = oldItem == newItem
        }
    }
}
