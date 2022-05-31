package com.noaa.shuckmanager

enum class PacketType(val code: Byte) {
    PING(0x00),
    HEALTH(0x01),
    CONFIG(0x02),
    DATA(0x08);
}