package com.kamel.ott750

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onChannelZap: ((Channel) -> Unit)? = null
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private val favLabels = mapOf(1 to "Cinema", 2 to "Sport", 3 to "News", 4 to "France", 5 to "Italie", 6 to "Nilesat")
    
    // Mode Zapping
    var isZappingMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvChannelName)
        val tvFavs: TextView = view.findViewById(R.id.tvFavs)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val ivZap: ImageView = view.findViewById(R.id.ivZap)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        val context = holder.root.context
        
        holder.tvName.text = channel.name
        holder.cbSelect.isChecked = channel.isSelected
        
        // Format Favs string
        val favNames = channel.favs.mapNotNull { favLabels[it] }.joinToString(", ")
        holder.tvFavs.text = if (favNames.isNotEmpty()) favNames else "Aucun favori"
        
        // Mode Zapping: masquer checkbox et afficher icône zap
        if (isZappingMode) {
            holder.cbSelect.visibility = View.GONE
            holder.ivZap.visibility = View.VISIBLE
            
            // Couleurs pour mode zapping (bien visible sur dark theme)
            holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.zapping_channel_name))
            holder.tvFavs.setTextColor(ContextCompat.getColor(context, R.color.zapping_fav_text))
            holder.root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        } else {
            holder.cbSelect.visibility = View.VISIBLE
            holder.ivZap.visibility = View.GONE
            
            // Couleurs pour mode normal (bien visible sur dark theme)
            if (channel.isSelected) {
                holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.selected_background))
                holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.selected_text))
            } else {
                holder.root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.channel_name_text))
            }
            holder.tvFavs.setTextColor(ContextCompat.getColor(context, R.color.fav_text))
        }

        holder.root.setOnClickListener {
            if (isZappingMode) {
                // Mode Zapping: envoyer commande au récepteur
                onChannelZap?.invoke(channel)
            } else {
                // Mode normal: sélection pour favoris
                channel.isSelected = !channel.isSelected
                notifyItemChanged(position)
                onSelectionChanged(channels.count { it.isSelected })
            }
        }
    }

    override fun getItemCount() = channels.size

    fun updateList(newList: List<Channel>) {
        channels = newList
        notifyDataSetChanged()
    }
    
    fun getSelectedChannels(): List<Channel> {
        return channels.filter { it.isSelected }
    }
}
