package com.wahyudotdev.ble.parser

import java.util.*

class FitProM5 : BleParser {

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }

    // 9 Juni 2022 20:11:26
    // year 22d -> 010110b
    // month 6d -> 0110b
    // day 9d -> 01001b
    // hour 20d -> 10100b
    // min 11d -> 001011b
    // sec 26d -> 011010b
    // 01011001 10010011 01000010 11011010
    // 0x59     0x93     0x42     0xDA
    // Final payload : 0xCD 0x00 0x09 0x12 0x01 0x01 0x00 0x04 0x59 0x93 0x42 0xDA
    override fun setDateTime(dt: Calendar): ByteArray {
        val year =
            String.format("%6s", Integer.toBinaryString(dt.get(Calendar.YEAR).minus(2000)))
                .replace(' ', '0')
        val month =
            String.format("%4s", Integer.toBinaryString(dt.get(Calendar.MONTH).plus(1))).replace(' ', '0')
        val day =
            String.format("%5s", Integer.toBinaryString(dt.get(Calendar.DATE))).replace(' ', '0')
        val hour =
            String.format("%5s", Integer.toBinaryString(dt.get(Calendar.HOUR))).replace(' ', '0')
        val min =
            String.format("%6s", Integer.toBinaryString(dt.get(Calendar.MINUTE))).replace(' ', '0')
        val sec =
            String.format("%6s", Integer.toBinaryString(dt.get(Calendar.SECOND))).replace(' ', '0')
        val join = "$year$month$day$hour$min$sec"

        val firstByte = Integer.parseInt(join, 0, 8, 2).toByte()
        val secondByte = Integer.parseInt(join, 8, 16, 2).toByte()
        val thirdByte = Integer.parseInt(join, 16, 24, 2).toByte()
        val fourthByte = Integer.parseInt(join, 24, 32, 2).toByte()

        return byteArrayOf(
            0xCD.toByte(),
            0x00,
            0x09,
            0x12,
            0x01,
            0x01,
            0x00,
            0x04,
            firstByte,
            secondByte,
            thirdByte,
            fourthByte
        )
    }

    override fun readHeartRate(): ByteArray {
        return byteArrayOfInts(0xCD, 0x00, 0x06, 0x12, 0x01, 0x18, 0x00, 0x01, 0x01)
    }

    override fun readSportsData(): ByteArray {
        return byteArrayOfInts(0xCD, 0x00, 0x06, 0x15, 0x01, 0x0D, 0x00, 0x01, 0x01)
    }
}