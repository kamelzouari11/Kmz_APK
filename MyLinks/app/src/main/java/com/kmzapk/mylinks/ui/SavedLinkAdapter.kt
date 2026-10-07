package com.kmzapk.mylinks.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kmzapk.mylinks.data.SavedLink
import com.kmzapk.mylinks.databinding.ItemSavedLinkBinding
import java.text.DateFormat
import java.util.Date

class SavedLinkAdapter(
    private val onOpen: (SavedLink) -> Unit
) : ListAdapter<SavedLink, SavedLinkAdapter.ViewHolder>(SavedLinkDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemSavedLinkBinding.inflate(inflater, parent, false)
        return ViewHolder(binding, onOpen)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSavedLinkBinding,
        private val onOpen: (SavedLink) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(link: SavedLink) {
            binding.titleText.text = link.title
            binding.categoryText.text = link.category
            binding.addressText.text = link.address.orEmpty()
            binding.addressText.visibility = if (link.address.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tagsText.text = link.tags
            binding.tagsText.visibility = if (link.tags.isBlank()) View.GONE else View.VISIBLE
            ProfileImageLoader.load(binding.profileImage, link.profileImageUrl)
            binding.profileImage.setOnClickListener {
                LinkOpener.open(it.context, link.originalUrl)
            }
            binding.sourceText.text = link.source
            binding.savedDateText.text = if (link.publishedAt != null) {
                "Published: ${DateFormat.getDateInstance().format(Date(link.publishedAt))}"
            } else if (link.createdAt > 0) {
                "Saved: ${DateFormat.getDateInstance().format(Date(link.createdAt))}"
            } else ""
            binding.aiCheckedText.visibility = if (link.aiCheckedAt != null) View.VISIBLE else View.GONE
            binding.aiCheckedText.text = when (link.aiCheckStatus) {
                "failed" -> "AI attempt failed"
                "unavailable" -> "Link unavailable (HTTP 404)"
                "empty" -> "AI checked: no new details"
                else -> "AI checked"
            }
            binding.urlText.text = link.originalUrl

            binding.urlText.setOnClickListener {
                LinkOpener.open(it.context, link.originalUrl)
            }
            binding.root.setOnClickListener {
                onOpen(link)
            }
        }
    }
}

class SavedLinkDiffCallback : DiffUtil.ItemCallback<SavedLink>() {
    override fun areItemsTheSame(oldItem: SavedLink, newItem: SavedLink): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: SavedLink, newItem: SavedLink): Boolean = oldItem == newItem
}
