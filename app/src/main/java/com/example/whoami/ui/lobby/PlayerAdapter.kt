package com.example.whoami.ui.lobby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.whoami.databinding.ItemPlayerBinding
import com.example.whoami.util.PlayerColors

class PlayerAdapter : ListAdapter<PlayerAdapter.Row, PlayerAdapter.VH>(DIFF) {

    data class Row(
        val uid: String,
        val name: String,
        val colorIndex: Int,
        val isHost: Boolean,
        val isMe: Boolean
    )

    class VH(val b: ItemPlayerBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val b = holder.b
        val color = PlayerColors.color(b.root.context, row.colorIndex)

        b.tvAvatar.text = row.name.take(1).uppercase().ifBlank { "?" }
        b.tvAvatar.background.mutate().setTint(color)
        b.tvName.text = if (row.isMe) "${row.name} (you)" else row.name
        b.tvBadge.isVisible = row.isHost
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.uid == newItem.uid
            override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
        }
    }
}
