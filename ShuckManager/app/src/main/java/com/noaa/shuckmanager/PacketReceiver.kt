package com.noaa.shuckmanager

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketReceiver {
    private val SYNC_PATTERN = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    private val packetBuffer = ArrayDeque<Packet>()

    enum class STATE {
        SYNC,
        LENGTH,
        ID,
        DATA
    }

    private var currLength = 0
    private var currId: Byte = 0x0
    private var currData: ByteBuffer? = null

    private var dataLeft = 0
    private var state = STATE.SYNC
        set(value) {
            field = value
            // Log.i("PacketReceiver", "State changed: $value")
            when (value) {
                STATE.SYNC -> dataLeft = SYNC_PATTERN.size
                STATE.LENGTH -> dataLeft = 2
                STATE.ID -> dataLeft = 1
                STATE.DATA -> {
                    dataLeft = currLength - 1
                    currData = ByteBuffer.allocate(dataLeft).order(ByteOrder.LITTLE_ENDIAN)
                }
            }
        }

    init {
        state = STATE.SYNC
    }

    fun putByteArray(array: ByteArray) {
        // readBuffer.put(array)

        for (byte in array) {
            // Log.i("PacketReceiver", "$byte")
            when (state) {
                STATE.SYNC -> {
                    val index = 4 - dataLeft
                    if (index in SYNC_PATTERN.indices && byte == SYNC_PATTERN[index]) {
                        // Log.i("PacketReceiver", "Syncing: $byte hit at $index")
                        dataLeft--
                        if (dataLeft <= 0)
                            state = STATE.LENGTH
                    } else {
                        // Log.i("PacketReceiver", "Syncing: $byte missed at $index")
                        dataLeft = SYNC_PATTERN.size
                    }
                }
                STATE.LENGTH -> {
                    currLength = ((currLength ushr 8) or (byte.toInt() shl 8)) and 0xFFFF
                    Log.i("PacketReceiver", "Length: $byte with $currLength")
                    dataLeft--
                    if (dataLeft <= 0)
                        state = STATE.ID
                }
                STATE.ID -> {
                    currId = byte
                    Log.i("PacketReceiver", "ID: $byte")
                    if (currLength - 1 > 0) {
                        state = STATE.DATA
                    } else {
                        state = STATE.SYNC

                        // got empty packet
                        val packet = Packet(currId, byteArrayOf())
                        packetBuffer.addLast(packet)
                        // Log.i("PacketReceiver", "Packet complete: [${packet.id}]")
                    }
                }
                STATE.DATA -> {
                    if (currData != null) {
                        currData!!.put(byte)
                        // Log.i("PacketReceiver", "Data: $byte")
                        dataLeft--
                        if (dataLeft <= 0) {
                            state = STATE.SYNC
                            val packet = Packet(currId, currData!!.array())
                            packetBuffer.addLast(packet)
                            Log.i("PacketReceiver", "Packet complete: [${packet.id}] ${packet.data}")
                        }
                    }
                }
            }
        }
    }

    fun hasPackets(): Boolean {
        return !packetBuffer.isEmpty()
    }

    fun getPacket(): Packet {
        return packetBuffer.removeFirst()
    }
}

data class Packet(val id: Byte, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (id != other.id) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.toInt()
        result = 31 * result + data.contentHashCode()
        return result
    }
}