package com.noaa.shuckmanager

// Packet IDs
enum class PacketType(val code: Byte) {
    PING(0x00),
    HEALTH(0x01),
    CONFIG(0x02),
    RTC_ERROR(0x04),
    SD_ERROR(0x05),
    PICO_ERROR(0x06),
    CURRENT_DATA(0x07),
    DATA(0x08);
}