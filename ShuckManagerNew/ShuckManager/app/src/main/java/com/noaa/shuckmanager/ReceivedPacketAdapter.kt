package com.noaa.shuckmanager

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.received_packet.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.text.SimpleDateFormat
import android.os.Environment
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.regex.Pattern


class ReceivedPacketAdapter (private val items: List<Packet>):

    RecyclerView.Adapter<ReceivedPacketAdapter.ViewHolder>() {
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
        //override fun onBindViewHolder(holder: ViewHolder) {
        val item = items[position]
        holder.bind(item)
        //holder.bind(items)
    }

    class ViewHolder(private val view: View):
        RecyclerView.ViewHolder(view) {
        fun bind(packet: Packet) {

            //From Here: We are doing the same process where we take the packet.data
            //and decompose all of the bytes
            val buffer = ByteBuffer.wrap(packet.data).order(ByteOrder.LITTLE_ENDIAN)

            if(packet.id == PacketType.DATA.code) {

                val entries = mutableListOf<DataEntry>()


                // get ID byte
                val id = if (buffer.hasRemaining()) {
                    buffer.get()
                } else {
                    0
                }

                // get number of entries
                val entryCount = if (buffer.hasRemaining()) {
                    buffer.get().toInt()
                } else {
                    0
                }
                Log.i("Data", "$entryCount")


                for (i in 0 until entryCount) {
                    Log.i("Data", "$i")
                    if (buffer.hasRemaining()) {
                        val time = buffer.getInt()
                        val entry = buffer.getFloat()
                        entries.add(DataEntry(time, entry))
                    }

                }

                // get label, minus null terminator if possible
                val label = if (buffer.hasRemaining()) {
                    val len = buffer.remaining() - 1
                    val strArray = ByteArray(len)
                    buffer.get(strArray)
                    strArray.decodeToString()
                } else {
                    "inv"   // no label
                }


                val fileName = "${id}_${label}.csv"
                //val fileName = "test2.csv"
                val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/$fileName"
                try {
                    var fo = FileWriter(filePath,true)
                    fo.write("$entries" + "\n")
                    fo.close()
                }catch (ex:Exception){
                    print(ex.message)
                }

                //Uncomment this section if you want the requested data to show up on the screen
                /*
                entries.forEach {
                    //it.entryValue is the actual data value

                    Log.i("ReceivedPacket", "$id\t${it.unixTime}\t${it.entryValue}\t$label")
                    //val dataInfo = "$id\t${it.unixTime}\t${it.entryValue}\t$label"
                    val idNumber = id

                    val time = it.unixTime.toLong() * 1000
                    val date = Date(time)
                    val dateformat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    dateformat.setTimeZone(TimeZone.getTimeZone("UTC"))
                    print(dateformat)

                    val timeLabel = dateformat.format(date)
                    Log.i("testDate", timeLabel)
                    val value = it.entryValue
                    val type = label
                    val dataInfo = "${"ID:"}\t$id\t${"Date/Time: "}\t${dateformat.format(date)}\t${" Value:"}\t${it.entryValue}\t$label"
                    view.packet_content.text = "[${dataInfo}]"
                }*/
            }

            else if(packet.id == PacketType.CURRENT_DATA.code){

                val entries = mutableListOf<DataEntry>()

                // get ID byte
                val id = if (buffer.hasRemaining()) {
                    buffer.get()

                } else {
                    0
                }
                Log.i("CurrentReading", "This is the id")
                Log.i("CurrentReading", "$id")

                // get number of entries
                val entryCount = if (buffer.hasRemaining()) {
                    buffer.get().toInt()

                } else {
                    0
                }
                Log.i("CurrentReading", "This is the entry count")
                Log.i("CurrentReading", "$entryCount")
                //val time = buffer.getFloat()
                val pH = buffer.getFloat()
                val temperature = buffer.getFloat()
                val salinity = buffer.getFloat()
                val conductivity = buffer.getFloat()
                Log.i("CurrentReading", "$pH")
                Log.i("CurrentReading", "$temperature")
                val idNumber = id
                val dataInfo = "${"ID:"}\t$id\t${" pH:"}\t${pH}\t${", tp:"}\t${temperature}\t${", sa:"}\t${salinity}\t${", co:"}\t${conductivity}"
                view.packet_content.text = "[${dataInfo}]"
            }

            else {
                //view.packet_content.text = "[${packet.id}] ${packet.data.toHexString()}"
                if (packet.id == PacketType.PING.code){
                    view.packet_content.text = "Ping Success!"
                } //Here is where the issue might be. Because the device is sending back a health packet, it
                //  is printing this statement, despite there being an error or not
                else if(packet.id == PacketType.HEALTH.code){
                    view.packet_content.text = "Device is healthy!"
                }
                else if(packet.id == PacketType.RTC_ERROR.code){
                    view.packet_content.text = "Device NOT healthy: RTC is bad!!!"
                }
                else if(packet.id == PacketType.SD_ERROR.code){
                    view.packet_content.text = "Device NOT healthy: SD is bad!!!"
                }
                else if(packet.id == PacketType.PICO_ERROR.code){
                    view.packet_content.text = "picoPH is not working correctly"
                }
                else if(packet.id == PacketType.CONFIG.code){
                    view.packet_content.text = "Configuration complete"
                }
            }
        }

        //AX: This will print out the data as a HEX string.
        //view.packet_content.text = "[${String(packet.id)}] ${packet.data.toHexString()}"

    }
}

fun ByteArray.toHexString(): String = joinToString(separator = " ", prefix = "0x") { String.format("%02X", it)}