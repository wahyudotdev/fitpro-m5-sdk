package com.wahyudotdev.ble.parser

import java.util.Calendar

interface BleParser {
    fun setDateTime(dt: Calendar): ByteArray
    fun readHeartRate() : ByteArray
    fun readSportsData(): ByteArray
}