package com.noaa.shuckmanager

// Sensor data entry, with time stamp and float value
data class DataEntry(
    val unixTime: Int,
    val entryValue: Float
)
