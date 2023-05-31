package com.noaa.shuckmanager

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Packet receipt manager, reads byte arrays and converts them to packets
class PacketReceiver {
    // Sync pattern, used to recognize new packets in a stream of bytes
    private val SYNC_PATTERN = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    // FIFO queue of complete packets received that have not been read externally
    private val packetBuffer = ArrayDeque<Packet>()

    // Receiver state machine states
    enum class STATE {
        SYNC,
        LENGTH,
        ID,
        DATA
    }

    // Length field, short
    private var currLength = 0

    // ID field, byte
    private var currId: Byte = 0x0

    // Data, bytes
    private var currData: ByteBuffer? = null

    // Bytes needed to be read in the current state
    private var dataLeft = 0

    // Current state
    private var state = STATE.SYNC
        set(value) {
            field = value

            // Set data left based on new state
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

    // Initialize state to SYNC on startup
    init {
        state = STATE.SYNC
    }

    // Receive an array of bytes, advancing state machine or recognizing new packets
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