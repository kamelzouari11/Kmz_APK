package com.kamel.ott750

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class StbChannelAdapter(
    private var channels: List<StbChannel>,
    private val onSelectionChanged: (Int) -> Unit,
    private val onChannelZap: ((StbChannel) -> Unit)? = null
) : RecyclerView.Adapter<StbChannelAdapter.ViewHolder>() {

    var favoriteGroups: List<FavoriteGroup> = emptyList()
    
    // Map des préfixes vers noms de satellites
    var satelliteNames: Map<String, String> = emptyMap()
    
    var isZappingMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvChannelName)
        val tvSatellite: TextView = view.findViewById(R.id.tvSatellite)
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
        
        // Nom avec indicateur HD/Radio
        val hdBadge = if (channel.isHD) " 📺" else ""
        val typeBadge = if (channel.programType == 0) " 📻" else ""
        holder.tvName.text = "${channel.name}$hdBadge$typeBadge"
        
        // Nom du satellite (basé sur le préfixe ProgramId) et Provider
        val prefix = channel.programId.take(7)
        val satName = satelliteNames[prefix] ?: prefix
        
        val providerText = if (channel.provider.isNotEmpty()) " | ${channel.provider}" else ""
        holder.tvSatellite.text = "${satName.lowercase()}$providerText"
        // Force une couleur visible (gris clair)
        holder.tvSatellite.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        
        holder.cbSelect.isChecked = channel.isSelected
        
        // Groupes de favoris
        val favNames = channel.favorGroupIds.mapNotNull { id -> 
            favoriteGroups.find { it.id == id }?.name 
        }.joinToString(", ")
        holder.tvFavs.text = if (favNames.isNotEmpty()) "★ $favNames" else ""
        holder.tvFavs.visibility = if (favNames.isNotEmpty()) View.VISIBLE else View.GONE
        
        // Mode Zapping
        if (isZappingMode) {
            holder.cbSelect.visibility = View.GONE
            holder.ivZap.visibility = View.VISIBLE
            holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.zapping_channel_name))
            holder.root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        } else {
            holder.cbSelect.visibility = View.VISIBLE
            holder.ivZap.visibility = View.GONE
            
            if (channel.isSelected) {
                holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.selected_background))
                holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.selected_text))
            } else {
                holder.root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                holder.tvName.setTextColor(ContextCompat.getColor(context, R.color.channel_name_text))
            }
        }

        holder.root.setOnClickListener {
            if (isZappingMode) {
                onChannelZap?.invoke(channel)
            } else {
                channel.isSelected = !channel.isSelected
                notifyItemChanged(position)
                onSelectionChanged(channels.count { it.isSelected })
            }
        }
        
        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (channel.isSelected != isChecked) {
                channel.isSelected = isChecked
                onSelectionChanged(channels.count { it.isSelected })
            }
        }
    }

    override fun getItemCount() = channels.size

    fun updateList(newList: List<StbChannel>) {
        channels = newList
        notifyDataSetChanged()
    }
    
    fun getSelectedChannels(): List<StbChannel> {
        return channels.filter { it.isSelected }
    }
    
    fun clearSelection() {
        channels.forEach { it.isSelected = false }
        notifyDataSetChanged()
    }
}
