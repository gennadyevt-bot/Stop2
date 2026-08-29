package com.stopvpn.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ServerAdapter(
    private val servers: List<ServerInfo>,
    private val onClick: (ServerInfo) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    private var selectedServerId: String? = null
    private var currentStatus: VpnStatus = VpnStatus.DISCONNECTED

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.cardServer)
        val tvName: TextView = itemView.findViewById(R.id.tvServerName)
        val tvCountry: TextView = itemView.findViewById(R.id.tvServerCountry)
        val tvStatus: TextView = itemView.findViewById(R.id.tvServerStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.tvName.text = "${server.flagEmoji} ${server.name}"
        holder.tvCountry.text = server.country

        val isSelected = server.id == selectedServerId
        val isConnected = isSelected && currentStatus == VpnStatus.CONNECTED

        holder.tvStatus.text = when {
            isConnected -> "Подключено"
            isSelected && currentStatus == VpnStatus.CONNECTING -> "Подключение..."
            else -> "Нажмите для подключения"
        }

        holder.tvStatus.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (isConnected) android.R.color.holo_green_dark else android.R.color.darker_gray
            )
        )

        holder.card.setCardBackgroundColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (isSelected) android.R.color.white else android.R.color.white
            )
        )

        holder.card.setOnClickListener { onClick(server) }
    }

    override fun getItemCount(): Int = servers.size

    fun setSelectedServer(id: String?) {
        selectedServerId = id
        notifyDataSetChanged()
    }

    fun setStatus(status: VpnStatus) {
        currentStatus = status
        notifyDataSetChanged()
    }
}
