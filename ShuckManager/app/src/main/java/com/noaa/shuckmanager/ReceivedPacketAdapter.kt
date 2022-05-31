package com.noaa.shuckmanager

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.received_packet.view.*

class ReceivedPacketAdapter (
    private val items: List<Packet>
) : RecyclerView.Adapter<ReceivedPacketAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.received_packet,
            parent,
            false
        )
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View
    ) : RecyclerView.ViewHolder(view) {
        fun bind(packet: Packet) {
            view.packet_content.text = "[${packet.id}] ${packet.data.toHexString()}"
        }
    }

}

fun ByteArray.toHexString(): String = joinToString(separator = " ", prefix = "0x") { String.format("%02X", it)}